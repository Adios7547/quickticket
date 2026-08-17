package com.quickticket.captcha.service;

import com.quickticket.common.error.BusinessException;
import com.quickticket.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 사칙연산 캡차. 매크로 방지가 목적이나, 텍스트 사칙연산은 자동화로 쉽게 풀리므로
 * 실질 효과는 봇 판별이 아니라 좌석 선점 요청의 지연·속도 제한이다.
 * (진짜 봇 판별이 필요하면 이미지 왜곡 CAPTCHA·reCAPTCHA로 교체 가능한 구조)
 *
 * 무작위 "시점" 발동(B안): 세션마다 서버가 무작위 발동 시각(due)을 부여하고,
 * 그 시각이 지나면 대기열 순번 폴링 응답의 captcha_required 가 true 로 바뀐다.
 * 봇이 순번을 예측해도 발동 시각은 알 수 없다.
 */
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final String DUE_KEY = "captcha:due:";           // captcha:due:{eventId}:{session}
    private static final String PASSED_KEY = "captcha:passed:";     // captcha:passed:{eventId}:{session}
    private static final String CHALLENGE_KEY = "captcha:challenge:"; // captcha:challenge:{challengeId}
    private static final String RATE_KEY = "captcha:rate:";         // captcha:rate:{eventId}:{session}
    private static final String SEAT_TOKEN_KEY = "seat:token:";     // seat:token:{token}

    private static final String DELIM = "|";
    private static final Duration CHALLENGE_TTL = Duration.ofSeconds(60);
    // 한 번 통과하면 같은 세션에는 이벤트 진행 동안 다시 묻지 않는다 (발동 주기보다 길게)
    private static final Duration PASSED_TTL = Duration.ofHours(1);
    private static final Duration SEAT_TOKEN_TTL = Duration.ofSeconds(300);
    private static final Duration DUE_TTL = Duration.ofHours(1);

    // rate-limit: 10초 창에서 최대 5회 발급
    private static final Duration RATE_WINDOW = Duration.ofSeconds(10);
    private static final long RATE_MAX = 5;

    private final StringRedisTemplate redisTemplate;

    @Value("${quickticket.captcha.min-delay-sec:5}")
    private long minDelaySec;

    @Value("${quickticket.captcha.max-delay-sec:20}")
    private long maxDelaySec;

    /**
     * 대기열 순번 폴링 시 호출. 이 세션이 지금 캡차를 풀어야 하는 상태인지 판정한다.
     * 발동 시각이 없으면 이번에 무작위로 부여하고(아직 미도래), 이미 통과했으면 false.
     */
    public boolean isChallengeRequired(Long eventId, String sessionKey) {
        if (sessionKey == null) {
            return false;
        }
        if (Boolean.TRUE.equals(redisTemplate.hasKey(passedKey(eventId, sessionKey)))) {
            return false;
        }
        long now = System.currentTimeMillis();
        long due = resolveDue(eventId, sessionKey, now);
        return now >= due;
    }

    public CaptchaChallengeResult issueChallenge(Long eventId, String sessionKey) {
        requireSession(sessionKey);
        // B안 핵심: 서버가 정한 무작위 발동 시각에 도달한 세션만 문제를 받을 수 있다.
        // 아직 시각 전이거나 이미 통과한 세션은 거부 → 봇이 미리 풀고 넘어가는 우회를 차단한다.
        if (!isChallengeRequired(eventId, sessionKey)) {
            throw new BusinessException(ErrorCode.CAPTCHA_NOT_REQUIRED);
        }
        checkRateLimit(eventId, sessionKey);

        Problem problem = Problem.random();
        String challengeId = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(
                challengeKey(challengeId), sessionKey + DELIM + problem.answer(), CHALLENGE_TTL);

        return new CaptchaChallengeResult(challengeId, problem.question(), CHALLENGE_TTL.toSeconds());
    }

    public CaptchaVerifyResult verify(Long eventId, String sessionKey, String challengeId, int answer) {
        requireSession(sessionKey);

        String stored = redisTemplate.opsForValue().get(challengeKey(challengeId));
        if (stored == null) {
            throw new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND);
        }
        // 한 문제는 한 번만 시도 가능 — 정답/오답 관계없이 소비하여 무차별 대입을 막는다
        redisTemplate.delete(challengeKey(challengeId));

        String[] parts = stored.split("\\" + DELIM, 2);
        String ownerSession = parts[0];
        int correctAnswer = Integer.parseInt(parts[1]);
        if (!ownerSession.equals(sessionKey)) {
            throw new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND);
        }
        if (answer != correctAnswer) {
            throw new BusinessException(ErrorCode.CAPTCHA_FAILED);
        }

        redisTemplate.opsForValue().set(passedKey(eventId, sessionKey), "1", PASSED_TTL);
        redisTemplate.delete(dueKey(eventId, sessionKey)); // 통과 후 재발동 방지
        String seatAccessToken = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(
                seatTokenKey(seatAccessToken), sessionKey + DELIM + eventId, SEAT_TOKEN_TTL);

        return new CaptchaVerifyResult(seatAccessToken, SEAT_TOKEN_TTL.toSeconds());
    }

    /** 세션의 무작위 발동 시각(epoch ms)을 조회하고, 없으면 새로 부여한다. */
    private long resolveDue(Long eventId, String sessionKey, long now) {
        String key = dueKey(eventId, sessionKey);
        String existing = redisTemplate.opsForValue().get(key);
        if (existing != null) {
            return Long.parseLong(existing);
        }
        long delaySec = minDelaySec >= maxDelaySec ? minDelaySec
                : ThreadLocalRandom.current().nextLong(minDelaySec, maxDelaySec + 1);
        long due = now + delaySec * 1000;
        redisTemplate.opsForValue().set(key, Long.toString(due), DUE_TTL);
        return due;
    }

    private void checkRateLimit(Long eventId, String sessionKey) {
        String key = rateKey(eventId, sessionKey);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, RATE_WINDOW);
        }
        if (count != null && count > RATE_MAX) {
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }
    }

    private void requireSession(String sessionKey) {
        if (sessionKey == null) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
    }

    private String dueKey(Long eventId, String sessionKey) {
        return DUE_KEY + eventId + ":" + sessionKey;
    }

    private String passedKey(Long eventId, String sessionKey) {
        return PASSED_KEY + eventId + ":" + sessionKey;
    }

    private String rateKey(Long eventId, String sessionKey) {
        return RATE_KEY + eventId + ":" + sessionKey;
    }

    private String challengeKey(String challengeId) {
        return CHALLENGE_KEY + challengeId;
    }

    private String seatTokenKey(String token) {
        return SEAT_TOKEN_KEY + token;
    }

    /** 서비스 내부 전달용 결과 — 컨트롤러에서 응답 DTO로 변환 */
    public record CaptchaChallengeResult(String challengeId, String question, long expiresInSec) {
    }

    public record CaptchaVerifyResult(String seatAccessToken, long tokenExpiresInSec) {
    }

    /** 무작위 사칙연산 문제 (음수·큰 수 회피) */
    private record Problem(String question, int answer) {
        static Problem random() {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            return switch (r.nextInt(3)) {
                case 0 -> {
                    int a = r.nextInt(1, 100), b = r.nextInt(1, 100);
                    yield new Problem(a + " + " + b + " = ?", a + b);
                }
                case 1 -> {
                    int a = r.nextInt(1, 100), b = r.nextInt(1, a + 1); // b <= a 로 음수 방지
                    yield new Problem(a + " - " + b + " = ?", a - b);
                }
                default -> {
                    int a = r.nextInt(1, 10), b = r.nextInt(1, 10);
                    yield new Problem(a + " x " + b + " = ?", a * b);
                }
            };
        }
    }
}
