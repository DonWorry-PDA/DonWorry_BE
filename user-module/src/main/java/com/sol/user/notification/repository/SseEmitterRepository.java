package com.sol.user.notification.repository;

import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class SseEmitterRepository {

    private final ConcurrentHashMap<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter save(Long userId, SseEmitter emitter) {
        emitters.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(emitter);
        return emitter;
    }

    public void delete(Long userId, SseEmitter emitter) {
        Set<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters != null) {
            userEmitters.remove(emitter);
            if (userEmitters.isEmpty()) {
                emitters.remove(userId);
            }
        }
    }

    public Set<SseEmitter> findByUserId(Long userId) {
        return emitters.getOrDefault(userId, Collections.emptySet());
    }

    public ConcurrentHashMap<Long, Set<SseEmitter>> findAll() {
        return emitters;
    }
}
