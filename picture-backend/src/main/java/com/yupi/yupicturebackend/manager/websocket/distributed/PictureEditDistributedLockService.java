package com.yupi.yupicturebackend.manager.websocket.distributed;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图片“编辑锁”：
 * - 分布式：用 Redis SETNX + TTL，确保多实例下同一张图同一时刻只有一个编辑者
 * - 降级：Redis 不可用时，用本机内存 Map（单机模式仍可运行）
 */
@Component
@Slf4j
public class PictureEditDistributedLockService {

    private static final String KEY_PREFIX = "ws:picture:edit:lock:";

    /**
     * 编辑锁 TTL：防止客户端断线未释放导致永久占用
     */
    private static final Duration LOCK_TTL = Duration.ofMinutes(3);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 降级用：key pictureId, value userId
    private final Map<Long, Long> localLocks = new ConcurrentHashMap<>();

    private String key(Long pictureId) {
        return KEY_PREFIX + pictureId;
    }

    /**
     * 尝试加锁（进入编辑态）
     */
    public boolean tryLock(Long pictureId, Long userId) {
        try {
            Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(key(pictureId), String.valueOf(userId), LOCK_TTL);
            if (Boolean.TRUE.equals(ok)) {
                return true;
            }
            // 可重入：如果锁已经是自己持有，则允许（并顺便续期）
            if (isOwner(pictureId, userId)) {
                refreshIfOwner(pictureId, userId);
                return true;
            }
            return false;
        } catch (Exception e) {
            // 降级：单机内存锁
            Long existing = localLocks.putIfAbsent(pictureId, userId);
            if (existing == null) {
                return true;
            }
            // 可重入：同一用户再次 enter
            return existing.equals(userId);
        }
    }

    /**
     * 判断当前 user 是否持有锁（用于 EDIT_ACTION）
     */
    public boolean isOwner(Long pictureId, Long userId) {
        try {
            String v = stringRedisTemplate.opsForValue().get(key(pictureId));
            return v != null && v.equals(String.valueOf(userId));
        } catch (Exception e) {
            Long v = localLocks.get(pictureId);
            return v != null && v.equals(userId);
        }
    }

    /**
     * 续期：编辑过程中延长 TTL，避免长编辑导致锁过期被他人抢占
     */
    public void refreshIfOwner(Long pictureId, Long userId) {
        try {
            if (isOwner(pictureId, userId)) {
                stringRedisTemplate.expire(key(pictureId), LOCK_TTL);
            }
        } catch (Exception ignored) {
            // 降级模式无需处理 TTL
        }
    }

    /**
     * 释放锁（退出编辑态/断开连接）
     */
    public void unlockIfOwner(Long pictureId, Long userId) {
        try {
            if (isOwner(pictureId, userId)) {
                stringRedisTemplate.delete(key(pictureId));
            }
        } catch (Exception e) {
            Long v = localLocks.get(pictureId);
            if (v != null && v.equals(userId)) {
                localLocks.remove(pictureId);
            }
        }
    }
}


