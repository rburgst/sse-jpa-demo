package com.example.ssejpademo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.repository.CrudRepository;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.transaction.Transactional;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootApplication
@EnableScheduling
public class SseJpaDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SseJpaDemoApplication.class, args);
    }
}

@Entity
class LogEntity {
    @Id
    @GeneratedValue()
    private Long id;

    private String message;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

@Repository
interface LogRepository extends CrudRepository<LogEntity, Long> {
}

@Controller
class HomeController {
    private static final Logger logger = LoggerFactory.getLogger(HomeController.class);
    /**
     * Map from business partners to websocket sessions. This is used to improve lookups when receiving update notificatations.
     */
    List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    AtomicInteger counter = new AtomicInteger(1);

    private final LogRepository logRepository;

    HomeController(LogRepository logRepository) {
        this.logRepository = logRepository;
    }


    @Transactional
    @GetMapping("/")
    ModelAndView index() {
        final ModelAndView modelAndView = new ModelAndView("index");
        modelAndView.addObject("count", logRepository.count());
        return modelAndView;
    }

    @Transactional
    @PostMapping("/add")
    public String addLog() {
        LogEntity newLog = new LogEntity();
        newLog.setMessage("new message at " + System.currentTimeMillis());
        logRepository.save(newLog);
        return "redirect:/";
    }

    @GetMapping("/sse/client")
    SseEmitter register() {
        final SseEmitter emitter = new SseEmitter();
        emitter.onCompletion(() -> onAfterConnectionCompleted(emitter));
        emitter.onTimeout(() -> onAfterConnectionTimeout(emitter));
        emitters.add(emitter);

        int startCount = (int) logRepository.count();

        sendChangeEventSse(emitter, startCount);
        return emitter;
    }

    void onAfterConnectionCompleted(final SseEmitter emitter) {
        removeSseEmitter(emitter);
    }

    void onAfterConnectionTimeout(final SseEmitter emitter) {
        removeSseEmitter(emitter);
    }

    private void removeSseEmitter(final SseEmitter emitter) {
        emitters.remove(emitter);
    }

    private void sendChangeEventSse(final SseEmitter emitter, final int count) {
        final String json = "{value: " + count + "}";
        try {
            emitter.send(json, MediaType.APPLICATION_JSON_UTF8);
        } catch (IOException e) {
            logger.error("error sending", e);
            removeSseEmitter(emitter);
        }
    }

    @Scheduled(fixedRate = 500000L)
    public void notifyEmitters() {
        final int newCount = counter.incrementAndGet();
        logger.info("sending out notification for {}", newCount);
        final List<SseEmitter> sseEmitters = new ArrayList<>(emitters);
        sseEmitters.forEach(sseEmitter ->
                sendChangeEventSse(sseEmitter, newCount)
        );
    }

}