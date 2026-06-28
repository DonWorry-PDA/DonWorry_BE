package com.sol.user.notification.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SseEmitterRepositoryTest {

    private SseEmitterRepository repository;

    @BeforeEach
    void setUp() {
        repository = new SseEmitterRepository();
    }

    @Test
    @DisplayName("emitter 저장 후 userId로 조회된다")
    void save_andFindByUserId() {
        SseEmitter emitter = mock(SseEmitter.class);
        repository.save(1L, emitter);

        Set<SseEmitter> result = repository.findByUserId(1L);

        assertThat(result).containsExactly(emitter);
    }

    @Test
    @DisplayName("같은 userId에 여러 emitter를 저장할 수 있다")
    void save_multipleEmittersForSameUser() {
        SseEmitter emitter1 = mock(SseEmitter.class);
        SseEmitter emitter2 = mock(SseEmitter.class);
        repository.save(1L, emitter1);
        repository.save(1L, emitter2);

        Set<SseEmitter> result = repository.findByUserId(1L);

        assertThat(result).containsExactlyInAnyOrder(emitter1, emitter2);
    }

    @Test
    @DisplayName("emitter 삭제 후 조회되지 않는다")
    void delete_removesEmitter() {
        SseEmitter emitter = mock(SseEmitter.class);
        repository.save(1L, emitter);

        repository.delete(1L, emitter);

        assertThat(repository.findByUserId(1L)).isEmpty();
    }

    @Test
    @DisplayName("마지막 emitter 삭제 시 userId 엔트리도 제거된다")
    void delete_removesUserEntryWhenEmpty() {
        SseEmitter emitter = mock(SseEmitter.class);
        repository.save(1L, emitter);
        repository.delete(1L, emitter);

        assertThat(repository.findAll()).doesNotContainKey(1L);
    }

    @Test
    @DisplayName("findAll은 저장된 모든 userId의 emitter를 반환한다")
    void findAll_returnsAllEmitters() {
        SseEmitter emitter1 = mock(SseEmitter.class);
        SseEmitter emitter2 = mock(SseEmitter.class);
        repository.save(1L, emitter1);
        repository.save(2L, emitter2);

        var result = repository.findAll();

        assertThat(result.get(1L)).containsExactly(emitter1);
        assertThat(result.get(2L)).containsExactly(emitter2);
    }

    @Test
    @DisplayName("등록하지 않은 userId 조회 시 빈 Set이 반환된다")
    void findByUserId_returnsEmptySetForUnknownUser() {
        assertThat(repository.findByUserId(999L)).isEmpty();
    }
}
