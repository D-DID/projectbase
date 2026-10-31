package com.underfaker.recallcheck.service.sync;

import com.underfaker.recallcheck.config.RecallAutoSyncProperties;
import com.underfaker.recallcheck.dto.response.RangeSyncResponse;
import com.underfaker.recallcheck.entity.User;
import com.underfaker.recallcheck.entity.enums.Role;
import com.underfaker.recallcheck.repository.RecallRepository;
import com.underfaker.recallcheck.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 10/31 — 리콜 데이터 자동 갱신(RecallAutoSyncScheduler). 외부 API·DB 없이 가짜로 돌린다.
 * (리포지토리는 java.lang.reflect.Proxy, 동기화 서비스는 하위 클래스로 대신한다)
 */
class RecallAutoSyncSchedulerTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 31);

    RecallAutoSyncProperties props;
    String latestPublishDate;
    Long firstAdminId;
    Set<Long> userIds;
    List<String> calls;
    RuntimeException failOnRange;
    RecallAutoSyncScheduler scheduler;
    Runnable duringRange;

    @BeforeEach
    void setUp() {
        props = new RecallAutoSyncProperties();
        latestPublishDate = "20261030";
        firstAdminId = 7L;
        userIds = new HashSet<>(Set.of(3L, 7L));
        calls = new ArrayList<>();
        failOnRange = null;
        duringRange = null;

        RecallRepository recalls = (RecallRepository) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{RecallRepository.class}, (proxy, m, args) -> {
                    if (m.getName().equals("findLatestPublishDate")) return latestPublishDate;
                    if (m.getName().equals("toString")) return "fakeRecallRepository";
                    throw new UnsupportedOperationException(m.getName());
                });
        UserRepository users = (UserRepository) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{UserRepository.class}, (proxy, m, args) -> {
                    if (m.getName().equals("existsById")) return userIds.contains((Long) args[0]);
                    if (m.getName().equals("findFirstByRoleOrderByIdAsc")) {
                        assertEquals(Role.ADMIN, args[0]);
                        return firstAdminId == null ? Optional.empty() : Optional.of(user(firstAdminId));
                    }
                    if (m.getName().equals("toString")) return "fakeUserRepository";
                    throw new UnsupportedOperationException(m.getName());
                });
        RecallSyncService sync = new RecallSyncService(null, null, null, null) {
            @Override
            public RangeSyncResponse syncRange(Long adminId, String from, String to,
                                               boolean withImages, int max, String categoryFilter) {
                calls.add("range " + adminId + " " + from + "~" + to + " images=" + withImages
                        + " max=" + max + " filter=" + categoryFilter);
                if (duringRange != null) duringRange.run();
                if (failOnRange != null) throw failOnRange;
                return new RangeSyncResponse(from, to, 7, 1, 3, 3, 0, withImages, null, null, null, List.of());
            }

            @Override
            public int syncImages(int limit) {
                calls.add("images " + limit);
                return 2;
            }
        };
        scheduler = new RecallAutoSyncScheduler(sync, recalls, users, props);
    }

    static User user(long id) throws Exception {
        Constructor<User> c = User.class.getDeclaredConstructor();
        c.setAccessible(true);
        User u = c.newInstance();
        Field f = User.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(u, id);
        return u;
    }

    @Test
    void 최근_7일을_본문만_받고_사진을_채운다_이력은_첫_관리자로() {
        RangeSyncResponse r = scheduler.run("정기", TODAY);
        assertNotNull(r);
        assertEquals(List.of("range 7 20261025~20261031 images=false max=0 filter=null", "images 30"), calls);
    }

    @Test
    void 서버가_꺼져_있던_빈틈을_마지막_공표일부터_받는다() {
        latestPublishDate = "20261003";
        scheduler.run("기동", TODAY);
        assertEquals("range 7 20261003~20261031 images=false max=0 filter=null", calls.get(0));
    }

    @Test
    void 관리자_지정값이_있으면_그_계정_없으면_첫_관리자() {
        props.setAdminId(3L);
        assertEquals(3L, scheduler.resolveAdminId());
        props.setAdminId(99L);   // 없는 계정
        assertEquals(7L, scheduler.resolveAdminId());
    }

    @Test
    void 관리자_계정이_없어도_적재는_한다_이력은_null() {
        firstAdminId = null;
        scheduler.run("정기", TODAY);
        assertTrue(calls.get(0).startsWith("range null "), calls.get(0));
    }

    @Test
    void 사진_한도가_0이면_사진은_안_채운다() {
        props.setImageLimit(0);
        scheduler.run("정기", TODAY);
        assertEquals(1, calls.size());
    }

    @Test
    void 실패해도_예외를_던지지_않고_다음_실행은_된다() {
        failOnRange = new IllegalStateException("API 응답 없음");
        assertNull(scheduler.run("정기", TODAY));
        failOnRange = null;
        assertNotNull(scheduler.run("정기", TODAY));
    }

    @Test
    void 앞_실행이_도는_중이면_건너뛴다() {
        List<RangeSyncResponse> nested = new ArrayList<>();
        duringRange = () -> nested.add(scheduler.run("정기", TODAY));
        assertNotNull(scheduler.run("기동", TODAY));
        assertEquals(1, nested.size());
        assertNull(nested.get(0));
        assertEquals(1L, calls.stream().filter(c -> c.startsWith("range")).count());
    }

    @Test
    void 꺼져_있으면_정기_실행이_아무것도_안_한다() {
        props.setEnabled(false);
        scheduler.onSchedule();
        assertTrue(calls.isEmpty());
    }
}
