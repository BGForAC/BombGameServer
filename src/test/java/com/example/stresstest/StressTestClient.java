package com.example.stresstest;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 游戏服务器压力测试客户端 v2.0
 *
 * 模拟真实玩家行为：
 *   1. 登录后 1~5 秒内，按 4 人一组陆续进入房间
 *   2. 全部就绪后房主开始游戏
 *   3. 游戏中模拟移动、放置炸弹（检查炸弹余量）
 *   4. 对局结束后自动返回房间，重新开始下一局
 *
 * 操作概率：70%移动 / 20%炸弹 / 10%空闲，间隔 10~50ms，心跳 5s，测试持续 600 秒。
 *
 * JVM 参数：
 *   -Dstress.host=127.0.0.1      服务器地址
 *   -Dstress.port=25565          服务器端口
 *   -Dstress.clients=100         模拟客户端数量（须为 4 的倍数）
 *   -Dstress.duration=600        测试持续秒数
 *   -Dstress.opMinMs=10          操作最小间隔(ms)
 *   -Dstress.opMaxMs=50          操作最大间隔(ms)
 *   -Dstress.moveProb=70         移动概率(%)
 *   -Dstress.bombProb=20         炸弹概率(%)
 *   -Dstress.rampUpMs=10         连接建立间隔(ms)
 */
public class StressTestClient {

    // ==================== 配置常量 ====================

    static final String SERVER_HOST = System.getProperty("stress.host", "127.0.0.1");
    static final int SERVER_PORT = Integer.getInteger("stress.port", 25565);
    static final int CLIENT_COUNT = Integer.getInteger("stress.clients", 800);
    static final int TEST_DURATION_SECONDS = Integer.getInteger("stress.duration", 600);
    static final int OP_INTERVAL_MIN_MS = Integer.getInteger("stress.opMinMs", 10);
    static final int OP_INTERVAL_MAX_MS = Integer.getInteger("stress.opMaxMs", 50);
    static final long HEARTBEAT_INTERVAL_MS = Long.getLong("stress.heartbeatMs", 5000L);
    static final int MOVE_PROBABILITY = Integer.getInteger("stress.moveProb", 70);
    static final int BOMB_PROBABILITY = Integer.getInteger("stress.bombProb", 20);
    static final int MAX_FRAME_LENGTH = 1024 * 1024;
    static final int CONNECT_TIMEOUT_MS = 5000;
    static final int CONNECT_RAMP_UP_INTERVAL_MS = Integer.getInteger("stress.rampUpMs", 10);
    static final int ROOM_SIZE = 4;

    // 房间加入延迟范围 (1~5 秒)
    static final int ROOM_JOIN_DELAY_MIN_MS = 1000;
    static final int ROOM_JOIN_DELAY_MAX_MS = 5000;

    // 游戏结束后等待重新开始的延迟
    static final int GAME_RESTART_DELAY_MS = 2000;

    // 炸弹引信时间(ms) — 用于本地炸弹槽位恢复估算
    static final int BOMB_FUSE_TIME_MS = 3000;
    // 默认最大炸弹数
    static final int DEFAULT_MAX_BOMB_COUNT = 1;

    // 延迟监测：被监测客户端比例 (0.0~1.0)，0 表示不监测任何客户端
    static final double MONITORED_RATIO = Double.parseDouble(System.getProperty("stress.monitoredRatio", "0.025"));

    // ==================== 命令类型常量 ====================

    static final int CMD_LOGIN = 0x0101;
    static final int CMD_HEARTBEAT = 0x0102;
    static final int CMD_MOVE = 0x0301;
    static final int CMD_ENTER_BASE_GAME = 0x0403;
    static final int CMD_BASE_GAME_CREATE_ROOM = 0x0404;
    static final int CMD_BASE_GAME_JOIN_ROOM = 0x0405;
    static final int CMD_BASE_GAME_LEAVE_ROOM = 0x0406;
    static final int CMD_BASE_GAME_CURRENT_ROOM_CHANGE = 0x0407;
    static final int CMD_BASE_GAME_REQ_ROOM_INFO = 0x0408;
    static final int CMD_BASE_GAME_CHANGE_CAREER = 0x040D;
    static final int CMD_BASE_GAME_READY = 0x040C;
    static final int CMD_BASE_GAME_START_GAME = 0x0410;
    static final int CMD_GAME_OVER = 0x0411;
    static final int CMD_PUT_BOMB = 0x0501;
    static final int CMD_BOMB_EXPLODE = 0x0502;
    static final int CMD_PLAYER_SYNC = 0x0602;
    static final int CMD_HP_CHANGE = 0x0601;
    static final int CMD_INVALID = 0x01FF;
    static final int CMD_ALERT = 0x01FE;
    static final int CMD_INFO = 0x01FD;

    // ==================== 房间分组协调 ====================

    /**
     * 每 4 个客户端共享一个房间组，用于协调房间创建/加入
     */
    static class RoomGroup {
        final int groupIndex;
        volatile int roomId = -1;           // 创建房间后设置
        volatile boolean roomCreated = false;
        final AtomicInteger membersJoined = new AtomicInteger(0);
        final AtomicInteger membersReady = new AtomicInteger(0);
        volatile boolean gameStarted = false;
        volatile boolean gameOver = false;

        RoomGroup(int groupIndex) {
            this.groupIndex = groupIndex;
        }
    }

    static final ConcurrentHashMap<Integer, RoomGroup> roomGroups = new ConcurrentHashMap<>();

    // ==================== 全局统计 ====================

    static final LongAdder connectAttempts = new LongAdder();
    static final LongAdder connectSuccesses = new LongAdder();
    static final LongAdder connectFailures = new LongAdder();
    static final LongAdder disconnections = new LongAdder();
    static final AtomicInteger activeConnections = new AtomicInteger(0);

    static final LongAdder totalMessagesSent = new LongAdder();
    static final LongAdder totalMessagesReceived = new LongAdder();
    static final LongAdder totalErrors = new LongAdder();
    static final LongAdder moveMessagesSent = new LongAdder();
    static final LongAdder bombMessagesSent = new LongAdder();
    static final LongAdder heartbeatMessagesSent = new LongAdder();
    static final LongAdder roomCreates = new LongAdder();
    static final LongAdder roomJoins = new LongAdder();
    static final LongAdder gamesStarted = new LongAdder();
    static final LongAdder gamesCompleted = new LongAdder();

    static final LongAdder totalLatencyMicros = new LongAdder();
    static final LongAdder latencyCount = new LongAdder();
    static final AtomicLong minLatencyMicros = new AtomicLong(Long.MAX_VALUE);
    static final AtomicLong maxLatencyMicros = new AtomicLong(0);

    static volatile long testStartTime = 0;
    static volatile long testEndTime = 0;

    static void recordConnectAttempt() { connectAttempts.increment(); }
    static void recordConnectSuccess() { connectSuccesses.increment(); activeConnections.incrementAndGet(); }
    static void recordConnectFailure() { connectFailures.increment(); }
    static void recordDisconnect() { disconnections.increment(); activeConnections.decrementAndGet(); }

    static void recordLatency(long micros) {
        totalLatencyMicros.add(micros);
        latencyCount.increment();
        long current;
        do { current = minLatencyMicros.get(); }
        while (micros < current && !minLatencyMicros.compareAndSet(current, micros));
        do { current = maxLatencyMicros.get(); }
        while (micros > current && !maxLatencyMicros.compareAndSet(current, micros));
    }

    static void printSnapshot(String label) {
        double elapsed = testStartTime > 0 ? (System.currentTimeMillis() - testStartTime) / 1000.0 : 0;
        long sent = totalMessagesSent.sum();
        long recv = totalMessagesReceived.sum();
        long errors = totalErrors.sum();
        int active = activeConnections.get();
        long rate = elapsed > 0 ? (long)(sent / elapsed) : 0;

        System.out.printf("%n===== %s (%.1f秒) =====%n", label, elapsed);
        System.out.printf("  活跃连接: %d  |  对局完成: %d  |  游戏启动: %d%n",
                active, gamesCompleted.sum(), gamesStarted.sum());
        System.out.printf("  发送消息: %d  |  接收消息: %d  |  错误: %d%n", sent, recv, errors);
        System.out.printf("  移动: %d  |  炸弹: %d  |  心跳: %d  |  创建房: %d  |  加入房: %d%n",
                moveMessagesSent.sum(), bombMessagesSent.sum(), heartbeatMessagesSent.sum(),
                roomCreates.sum(), roomJoins.sum());
        {
            long lc2 = latencyCount.sum();
            double avg = lc2 > 0 ? totalLatencyMicros.sum() / (double)lc2 / 1000.0 : 0;
            double min = lc2 > 0 ? (minLatencyMicros.get() == Long.MAX_VALUE ? 0 : minLatencyMicros.get() / 1000.0) : 0;
            double max = lc2 > 0 ? maxLatencyMicros.get() / 1000.0 : 0;
            System.out.printf("  响应延迟(ms): avg=%.2f  min=%.2f  max=%.2f  |  样本数=%d%n", avg, min, max, lc2);
        }
        System.out.printf("  吞吐量: %d msg/s%n", rate);
    }

    static void printFinalReport() {
        double actualDuration = (testEndTime - testStartTime) / 1000.0;
        long sent = totalMessagesSent.sum();
        long recv = totalMessagesReceived.sum();
        long errors = totalErrors.sum();
        long lc = latencyCount.sum();

        System.out.println();
        System.out.println(repeat("=", 70));
        System.out.println(center("压 力 测 试 报 告", 70));
        System.out.println(repeat("=", 70));
        System.out.printf("  服务器地址:          %s:%d%n", SERVER_HOST, SERVER_PORT);
        System.out.printf("  模拟客户端数量:      %d (%.0f 个房间/组)%n", CLIENT_COUNT, Math.ceil(CLIENT_COUNT / (double)ROOM_SIZE));
        System.out.printf("  计划测试时长:        %d 秒%n", TEST_DURATION_SECONDS);
        System.out.printf("  实际测试时长:        %.1f 秒%n", actualDuration);
        System.out.println(repeat("-", 70));
        System.out.printf("  连接尝试:            %d%n", connectAttempts.sum());
        System.out.printf("  连接成功:            %d%n", connectSuccesses.sum());
        System.out.printf("  连接失败:            %d%n", connectFailures.sum());
        System.out.printf("  断开连接:            %d%n", disconnections.sum());
        System.out.println(repeat("-", 70));
        System.out.printf("  创建房间:            %d%n", roomCreates.sum());
        System.out.printf("  加入房间:            %d%n", roomJoins.sum());
        System.out.printf("  启动游戏:            %d%n", gamesStarted.sum());
        System.out.printf("  完成对局:            %d%n", gamesCompleted.sum());
        System.out.println(repeat("-", 70));
        System.out.printf("  总发送消息:          %d%n", sent);
        System.out.printf("  总接收消息:          %d%n", recv);
        System.out.printf("  总错误数:            %d%n", errors);
        System.out.printf("  移动消息:            %d%n", moveMessagesSent.sum());
        System.out.printf("  放置炸弹消息:        %d%n", bombMessagesSent.sum());
        System.out.printf("  心跳消息:            %d%n", heartbeatMessagesSent.sum());
        System.out.println(repeat("-", 70));
        if (actualDuration > 0) {
            System.out.printf("  平均吞吐量:          %d msg/s%n", (long)(sent / actualDuration));
            System.out.printf("  平均接收速率:        %d msg/s%n", (long)(recv / actualDuration));
        }
        if (lc > 0) {
            double avgMs = totalLatencyMicros.sum() / (double)lc / 1000.0;
            double minMs = minLatencyMicros.get() == Long.MAX_VALUE ? 0 : minLatencyMicros.get() / 1000.0;
            double maxMs = maxLatencyMicros.get() / 1000.0;
            System.out.println(repeat("-", 70));
            System.out.println("  响应延迟(ms):");
            System.out.printf("    平均:              %.2f%n", avgMs);
            System.out.printf("    最小:              %.2f%n", minMs);
            System.out.printf("    最大:              %.2f%n", maxMs);
        }
        if (sent > 0 && errors > 0) {
            System.out.println(repeat("-", 70));
            System.out.printf("  错误率:              %.2f%%%n", errors * 100.0 / sent);
        }
        System.out.println(repeat("=", 70));
    }

    static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }

    static String center(String s, int width) {
        int padding = (width - s.length()) / 2;
        if (padding <= 0) return s;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < padding; i++) sb.append(' ');
        sb.append(s);
        return sb.toString();
    }

    // ==================== 虚拟客户端 Handler ====================

    static class VirtualClientHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private final int clientId;
        private final String username;
        private final Random random;
        private final ScheduledExecutorService scheduler;

        // 房间分组
        private final int groupIndex;
        private final boolean isGroupLeader;
        private final RoomGroup roomGroup;
        private final boolean monitored;  // 初始化时决定是否监测延迟

        private ChannelHandlerContext ctx;
        private String playerId;
        private volatile boolean loggedIn;
        private volatile boolean running;

        // 延迟追踪：发送消息时主动入队时间戳，接收回显时出队计算 RTT
        // 在发送端"决定采样"而非接收端"判断是否采样"
        // 心跳为 1:1 请求-响应，使用 volatile long 防止堆积导致虚高延迟
        private final ConcurrentLinkedQueue<Long> pendingMoveTimestamps = new ConcurrentLinkedQueue<>();
        private final ConcurrentLinkedQueue<Long> pendingBombTimestamps = new ConcurrentLinkedQueue<>();
        private volatile long lastHeartbeatSendNanos = 0;
        private static final int MAX_PENDING_MEASUREMENTS = 500;  // 防止队列无界增长

        // 房间/游戏状态
        private enum State { LOGGING_IN, ENTERING_ROOM, IN_ROOM, PLAYING, GAME_OVER }
        private volatile State state = State.LOGGING_IN;

        // 炸弹追踪
        private final int maxBombCount = DEFAULT_MAX_BOMB_COUNT;
        private volatile int bombsAvailable = DEFAULT_MAX_BOMB_COUNT;
        private final AtomicInteger bombsPlaced = new AtomicInteger(0);

        private ScheduledFuture<?> opFuture;
        private ScheduledFuture<?> heartbeatFuture;
        private ScheduledFuture<?> bombRefillFuture;
        private ScheduledFuture<?> roomJoinFuture;
        private ScheduledFuture<?> readyFuture;
        private ScheduledFuture<?> startGameFuture;
        private ScheduledFuture<?> restartFuture;

        VirtualClientHandler(int clientId, String username, ScheduledExecutorService scheduler) {
            this.clientId = clientId;
            this.username = username;
            this.scheduler = scheduler;
            this.random = new Random(System.nanoTime() + clientId);

            this.groupIndex = (clientId - 1) / ROOM_SIZE;
            this.isGroupLeader = (clientId - 1) % ROOM_SIZE == 0;
            this.roomGroup = roomGroups.computeIfAbsent(groupIndex, RoomGroup::new);
            // 初始化时决定：每 N 个客户端中监测 1 个（均匀分布在所有房间组）
            int monitorInterval = MONITORED_RATIO > 0 ? Math.max(1, (int) Math.round(1.0 / MONITORED_RATIO)) : Integer.MAX_VALUE;
            this.monitored = clientId % monitorInterval == 0;
            if (monitored) {
                System.out.println("[监测] 客户端#" + clientId + " (" + username + ") 被选为延迟监测节点, 每" + monitorInterval + "个选1个");
            }
        }

        // ==================== Netty 回调 ====================

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            this.ctx = ctx;
            recordConnectSuccess();
            sendLogin();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            running = false;
            state = State.GAME_OVER;
            recordDisconnect();
            cancelAllTasks();
            try { super.channelInactive(ctx); } catch (Exception ignored) {}
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
            try {
                int cmdTyp = msg.readInt();
                int bodyLen = msg.readableBytes();
                byte[] body = new byte[bodyLen];
                if (bodyLen > 0) msg.readBytes(body);
                String json = new String(body, StandardCharsets.UTF_8);

                totalMessagesReceived.increment();

                switch (cmdTyp) {
                    case CMD_LOGIN:
                        handleLoginResponse(json);
                        break;
                    case CMD_BASE_GAME_JOIN_ROOM:
                        handleJoinRoomResponse(json);
                        break;
                    case CMD_ENTER_BASE_GAME:
                        handleEnterGame(json);
                        break;
                    case CMD_GAME_OVER:
                        handleGameOver(json);
                        break;
                    case CMD_BASE_GAME_CURRENT_ROOM_CHANGE:
                        handleRoomChange(json);
                        break;
                    case CMD_BOMB_EXPLODE:
                        handleBombExplode(json);
                        break;
                    case CMD_PUT_BOMB:
                        // 广播回显：匹配自己的炸弹 → 出队并记录 RTT
                        if (playerId != null) {
                            String bombId = extractJsonString(json, "id");
                            if (!bombId.isEmpty() && bombId.startsWith(playerId + "-")) {
                                dequeueAndRecord(pendingBombTimestamps);
                            }
                        }
                        break;
                    case CMD_PLAYER_SYNC:
                        handlePlayerSync(json);
                        dequeueAndRecord(pendingMoveTimestamps);
                        break;
                    case CMD_HP_CHANGE:
                        handleHpChange(json);
                        break;
                    case CMD_HEARTBEAT:
                        recordHeartbeatLatency();
                        break;
                    default:
                        // 其他响应消息
                        break;
                }
            } catch (Exception e) {
                totalErrors.increment();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            totalErrors.increment();
            running = false;
            cancelAllTasks();
            ctx.close();
        }

        // ==================== 服务器消息处理 ====================

        private void handleLoginResponse(String json) {
            String pid = extractJsonString(json, "playerId");
            String result = extractJsonString(json, "result");
            if ("success".equals(result) && !pid.isEmpty()) {
                this.playerId = pid;
                loggedIn = true;
                running = true;
                state = State.ENTERING_ROOM;
                startHeartbeat();  // 登录后立即开始心跳，防止服务端超时断连
                // 1~5 秒内开始进入房间流程
                int delay = ROOM_JOIN_DELAY_MIN_MS +
                        random.nextInt(ROOM_JOIN_DELAY_MAX_MS - ROOM_JOIN_DELAY_MIN_MS + 1);
                scheduler.schedule(this::enterRoomFlow, delay, TimeUnit.MILLISECONDS);
            }
        }

        private void handleJoinRoomResponse(String json) {
            String result = extractJsonString(json, "result");
            if ("success".equals(result)) {
                roomGroup.membersJoined.incrementAndGet();
                roomJoins.increment();
                state = State.IN_ROOM;
                // 提取 roomId（如果是创建房间的响应）
                String roomIdStr = extractNestedJsonString(json, "info", "roomId");
                if (!roomIdStr.isEmpty() && roomGroup.roomId < 0) {
                    int rid = Integer.parseInt(roomIdStr);
                    roomGroup.roomId = rid;
                    roomGroup.roomCreated = true;
                }
                // 安排发送准备状态
                scheduleReady();
            }
        }

        private void handleEnterGame(String json) {
            state = State.PLAYING;
            roomGroup.gameStarted = true;
            roomGroup.gameOver = false;
            gamesStarted.increment();
            bombsAvailable = maxBombCount;
            bombsPlaced.set(0);
            // 新对局开始，清空残留的旧时间戳
            pendingMoveTimestamps.clear();
            pendingBombTimestamps.clear();
            startGameLoop();
        }

        private void handleGameOver(String json) {
            if (state != State.PLAYING) return;
            state = State.GAME_OVER;
            roomGroup.gameOver = true;
            roomGroup.gameStarted = false;
            roomGroup.membersReady.set(0);
            gamesCompleted.increment();
            stopGameLoop();
            bombsAvailable = maxBombCount;
            bombsPlaced.set(0);
            // 对局结束，丢弃未匹配的延迟测量
            pendingMoveTimestamps.clear();
            pendingBombTimestamps.clear();

            // 等待一段时间后重新开始
            // 重新发送职业选择，防止 setOutScene 清空 career 后 startGame 崩溃
            int delay = GAME_RESTART_DELAY_MS + random.nextInt(2000);
            restartFuture = scheduler.schedule(() -> {
                if (running && loggedIn && ctx.channel().isActive()) {
                    roomGroup.membersJoined.set(ROOM_SIZE); // 已在房间中
                    state = State.IN_ROOM;
                    sendChangeCareer();  // 重设职业，防止 attr/null.attr 错误
                    scheduleReady();
                }
            }, delay, TimeUnit.MILLISECONDS);
        }

        private void handleRoomChange(String json) {
            // 房间状态变化通知，刷新房间信息
            if (state == State.IN_ROOM || state == State.GAME_OVER) {
                // 房间已更新，可能可以重新开始
            }
        }

        private void handleBombExplode(String json) {
            // 炸弹爆炸，如果是自己的炸弹则恢复槽位
            String bombOwnerId = extractJsonString(json, "id");
            if (playerId != null && playerId.equals(bombOwnerId)) {
                int placed = bombsPlaced.decrementAndGet();
                if (placed < 0) bombsPlaced.set(0);
            }
        }

        private void handlePlayerSync(String json) {
            // 从 PLAYER_SYNC 中提取自己的炸弹状态
            if (playerId == null) return;
            try {
                String bombCountStr = extractNestedJsonString(json, playerId, "bombCount");
                if (!bombCountStr.isEmpty()) {
                    int bc = (int) Float.parseFloat(bombCountStr);
                    bombsAvailable = Math.max(0, bc);
                }
            } catch (Exception ignored) {}
        }

        private void handleHpChange(String json) {
            // HP 变化，记录自己的死亡
            String targetId = extractJsonString(json, "id");
            if (playerId != null && playerId.equals(targetId)) {
                String hpStr = extractJsonString(json, "hp");
                if (!hpStr.isEmpty()) {
                    try {
                        int hp = (int) Float.parseFloat(hpStr);
                        if (hp <= 0) {
                            // 玩家死亡，重置炸弹状态（等对局结束）
                            bombsAvailable = 0;
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        // ==================== 房间流程 ====================

        private void enterRoomFlow() {
            if (!running || !loggedIn) return;
            if (isGroupLeader) {
                createRoom();
            } else {
                joinRoomWithRetry();
            }
        }

        private void createRoom() {
            if (!running || !loggedIn || ctx == null || !ctx.channel().isActive()) return;
            String roomName = String.format("StressRoom_%04d", groupIndex);
            String json = "{\"roomName\":\"" + roomName +
                    "\",\"mapIndex\":\"0\",\"career\":\"Speed\",\"controlConfig\":\"0\"}";
            sendMessage(CMD_BASE_GAME_CREATE_ROOM, json);
            roomCreates.increment();
        }

        private void joinRoomWithRetry() {
            if (!running || !loggedIn) return;
            // 等待房主创建房间（最多等 3 秒）
            long deadline = System.currentTimeMillis() + 3000;
            scheduler.execute(() -> {
                while (System.currentTimeMillis() < deadline && roomGroup.roomId < 0 && running) {
                    try { Thread.sleep(100); } catch (InterruptedException e) { return; }
                }
                if (roomGroup.roomId > 0 && running && loggedIn) {
                    doJoinRoom(roomGroup.roomId);
                }
            });
        }

        private void doJoinRoom(int roomId) {
            if (ctx == null || !ctx.channel().isActive() || !loggedIn) return;
            String json = "{\"roomId\":\"" + roomId + "\"}";
            sendMessage(CMD_BASE_GAME_JOIN_ROOM, json);
        }

        private void scheduleReady() {
            // 等待所有成员加入后再准备
            int delay = 500 + random.nextInt(1500); // 0.5~2 秒后准备
            readyFuture = scheduler.schedule(() -> {
                if (!running || !loggedIn || ctx == null || !ctx.channel().isActive()) return;
                if (state == State.IN_ROOM) {
                    sendReady();
                    // 如果是房主且所有人都准备了，开始游戏
                    if (isGroupLeader) {
                        scheduleStartGame();
                    }
                }
            }, delay, TimeUnit.MILLISECONDS);
        }

        private void sendReady() {
            if (ctx == null || !ctx.channel().isActive()) return;
            sendMessage(CMD_BASE_GAME_READY, "{}");
            roomGroup.membersReady.incrementAndGet();
        }

        private void scheduleStartGame() {
            // 等待所有 4 人准备后再开始
            int checkDelay = 1000 + random.nextInt(3000); // 1~4 秒后检查
            startGameFuture = scheduler.schedule(() -> {
                if (!running || !loggedIn || ctx == null || !ctx.channel().isActive()) return;
                if (roomGroup.gameStarted) return;
                if (roomGroup.membersReady.get() >= ROOM_SIZE) {
                    sendStartGame();
                } else {
                    // 重试：再过 1 秒检查
                    if (running && !roomGroup.gameStarted) {
                        startGameFuture = scheduler.schedule(() -> {
                            if (running && !roomGroup.gameStarted && roomGroup.membersReady.get() >= ROOM_SIZE) {
                                sendStartGame();
                            }
                        }, 1000, TimeUnit.MILLISECONDS);
                    }
                }
            }, checkDelay, TimeUnit.MILLISECONDS);
        }

        private void sendStartGame() {
            if (ctx == null || !ctx.channel().isActive() || roomGroup.gameStarted) return;
            sendMessage(CMD_BASE_GAME_START_GAME, "{}");
        }

        private void sendChangeCareer() {
            if (ctx == null || !ctx.channel().isActive()) return;
            sendMessage(CMD_BASE_GAME_CHANGE_CAREER, "{\"career\":\"Speed\"}");
        }

        // ==================== 游戏循环 ====================

        private void startGameLoop() {
            running = true;
            long initialDelay = getRandomInterval();
            opFuture = scheduler.scheduleAtFixedRate(() -> {
                if (!running || state != State.PLAYING || ctx == null || !ctx.channel().isActive()) return;
                try {
                    int dice = random.nextInt(100);
                    if (dice < MOVE_PROBABILITY) {
                        sendMove();
                    } else if (dice < MOVE_PROBABILITY + BOMB_PROBABILITY) {
                        sendPutBombIfAvailable();
                    }
                    // else: idle
                } catch (Exception e) {
                    totalErrors.increment();
                }
            }, initialDelay, getRandomInterval(), TimeUnit.MILLISECONDS);
            startHeartbeat();
        }

        private void stopGameLoop() {
            if (opFuture != null) { opFuture.cancel(false); opFuture = null; }
            if (heartbeatFuture != null) { heartbeatFuture.cancel(false); heartbeatFuture = null; }
        }

        private void startHeartbeat() {
            if (heartbeatFuture != null) heartbeatFuture.cancel(false);
            heartbeatFuture = scheduler.scheduleAtFixedRate(
                    this::sendHeartbeat,
                    HEARTBEAT_INTERVAL_MS,
                    HEARTBEAT_INTERVAL_MS,
                    TimeUnit.MILLISECONDS
            );
        }

        // ==================== 消息发送 ====================

        private void sendLogin() {
            String json = "{\"username\":\"" + username + "\",\"password\":\"123456\"}";
            sendMessage(CMD_LOGIN, json);
        }

        private void sendMove() {
            if (state != State.PLAYING) return;
            int x = random.nextInt(2000) + 500;
            int z = random.nextInt(2000) + 500;
            float angle = random.nextFloat() * 360f;
            int sprinting = random.nextDouble() < 0.1 ? 1 : 0;
            String json = "{\"x\":\"" + x + "\",\"y\":\"0\",\"z\":\"" + z +
                    "\",\"angle\":\"" + angle + "\",\"sprinting\":\"" + sprinting + "\"}";
            enqueueMeasurement(pendingMoveTimestamps);
            sendMessage(CMD_MOVE, json);
            moveMessagesSent.increment();
        }

        private void sendPutBombIfAvailable() {
            if (state != State.PLAYING) return;
            if (bombsAvailable <= 0) return;
            int x = random.nextInt(2000) + 500;
            int z = random.nextInt(2000) + 500;
            String json = "{\"x\":\"" + x + "\",\"y\":\"0\",\"z\":\"" + z + "\"}";
            enqueueMeasurement(pendingBombTimestamps);
            sendMessage(CMD_PUT_BOMB, json);
            bombMessagesSent.increment();
            bombsAvailable--;
            bombsPlaced.incrementAndGet();

            // 安排炸弹槽位恢复（模拟炸弹爆炸后恢复）
            bombRefillFuture = scheduler.schedule(() -> {
                if (bombsAvailable < maxBombCount) {
                    bombsAvailable = Math.min(maxBombCount, bombsAvailable + 1);
                }
            }, BOMB_FUSE_TIME_MS, TimeUnit.MILLISECONDS);
        }

        private void sendHeartbeat() {
            if (!loggedIn || !running) return;
            if (ctx == null || !ctx.channel().isActive()) return;
            if (monitored) {
                lastHeartbeatSendNanos = System.nanoTime();
            }
            sendMessage(CMD_HEARTBEAT, "{}");
            heartbeatMessagesSent.increment();
        }

        private void recordHeartbeatLatency() {
            long sendNanos = lastHeartbeatSendNanos;
            if (sendNanos > 0) {
                long rttNanos = System.nanoTime() - sendNanos;
                recordLatency(rttNanos / 1000);
            }
        }

        private void sendMessage(int cmdTyp, String jsonBody) {
            if (ctx == null || !ctx.channel().isActive()) return;
            try {
                byte[] bodyBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
                ByteBuf payload = Unpooled.buffer(4 + bodyBytes.length);
                payload.writeInt(cmdTyp);
                payload.writeBytes(bodyBytes);

                ByteBuf frame = Unpooled.buffer(4 + payload.readableBytes());
                frame.writeInt(payload.readableBytes());
                frame.writeBytes(payload);

                ctx.writeAndFlush(frame);
                payload.release();
                totalMessagesSent.increment();
            } catch (Exception e) {
                totalErrors.increment();
            }
        }

        // ==================== 辅助方法 ====================

        private long getRandomInterval() {
            return OP_INTERVAL_MIN_MS +
                    random.nextInt(OP_INTERVAL_MAX_MS - OP_INTERVAL_MIN_MS + 1);
        }

        private void cancelAllTasks() {
            if (opFuture != null) { opFuture.cancel(false); opFuture = null; }
            if (heartbeatFuture != null) { heartbeatFuture.cancel(false); heartbeatFuture = null; }
            if (bombRefillFuture != null) { bombRefillFuture.cancel(false); bombRefillFuture = null; }
            if (roomJoinFuture != null) { roomJoinFuture.cancel(false); roomJoinFuture = null; }
            if (readyFuture != null) { readyFuture.cancel(false); readyFuture = null; }
            if (startGameFuture != null) { startGameFuture.cancel(false); startGameFuture = null; }
            if (restartFuture != null) { restartFuture.cancel(false); restartFuture = null; }
            pendingMoveTimestamps.clear();
            pendingBombTimestamps.clear();
        }

        // ==================== 延迟测量：发送入队 / 接收出队 ====================

        /** 发送端：将当前时间戳入队（仅被监测客户端生效） */
        private void enqueueMeasurement(ConcurrentLinkedQueue<Long> queue) {
            if (!monitored) return;
            if (queue.size() < MAX_PENDING_MEASUREMENTS) {
                queue.offer(System.nanoTime());
            }
        }

        /** 接收端：出队并计算 RTT（仅被监测客户端生效） */
        private void dequeueAndRecord(ConcurrentLinkedQueue<Long> queue) {
            if (!monitored) return;
            Long sendNanos = queue.poll();
            if (sendNanos != null) {
                long rttNanos = System.nanoTime() - sendNanos;
                recordLatency(rttNanos / 1000);
            }
        }

        // ==================== 简易 JSON 解析 ====================

        private static String extractJsonString(String json, String key) {
            String searchKey = "\"" + key + "\"";
            int keyIdx = json.indexOf(searchKey);
            if (keyIdx < 0) return "";
            int colonIdx = json.indexOf(':', keyIdx + searchKey.length());
            if (colonIdx < 0) return "";
            int start = colonIdx + 1;
            while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\t')) start++;
            if (start >= json.length()) return "";
            if (json.charAt(start) == '"') {
                start++;
                int end = json.indexOf('"', start);
                if (end < 0) return "";
                return json.substring(start, end);
            } else {
                int end = start;
                while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}' && json.charAt(end) != ' ') end++;
                return json.substring(start, end);
            }
        }

        private static String extractNestedJsonString(String json, String parentKey, String childKey) {
            String searchKey = "\"" + parentKey + "\"";
            int keyIdx = json.indexOf(searchKey);
            if (keyIdx < 0) return "";
            int colonIdx = json.indexOf(':', keyIdx + searchKey.length());
            if (colonIdx < 0) return "";
            int start = colonIdx + 1;
            while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\t')) start++;
            if (start >= json.length()) return "";
            if (json.charAt(start) == '{') {
                int braceCount = 0;
                int i = start;
                for (; i < json.length(); i++) {
                    if (json.charAt(i) == '{') braceCount++;
                    else if (json.charAt(i) == '}') braceCount--;
                    if (braceCount == 0) break;
                }
                if (i < json.length()) {
                    String nested = json.substring(start, i + 1);
                    return extractJsonString(nested, childKey);
                }
            }
            return "";
        }
    }

    // ==================== 主入口 ====================

    public static void main(String[] args) throws Exception {
        printBanner();

        // 验证客户端数量
        if (CLIENT_COUNT % ROOM_SIZE != 0) {
            System.err.println("[警告] 客户端数量(" + CLIENT_COUNT + ")不是" + ROOM_SIZE + "的倍数，" +
                    "最后一组人数将不足" + ROOM_SIZE + "人");
        }

        testStartTime = System.currentTimeMillis();

        NioEventLoopGroup group = new NioEventLoopGroup();
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(
                Math.min(Runtime.getRuntime().availableProcessors() * 4, CLIENT_COUNT / 2 + 10)
        );

        int roomCount = (int) Math.ceil(CLIENT_COUNT / (double) ROOM_SIZE);
        System.out.println("[配置] 服务器: " + SERVER_HOST + ":" + SERVER_PORT);
        System.out.println("[配置] 客户端数: " + CLIENT_COUNT + " (分为 " + roomCount + " 个房间, 每组" + ROOM_SIZE + "人)");
        System.out.println("[配置] 测试时长: " + TEST_DURATION_SECONDS + "秒");
        System.out.println("[配置] 操作间隔: " + OP_INTERVAL_MIN_MS + "~" + OP_INTERVAL_MAX_MS + "ms");
        System.out.println("[配置] 行为概率: 移动" + MOVE_PROBABILITY + "% / 炸弹" + BOMB_PROBABILITY +
                "% / 空闲" + (100 - MOVE_PROBABILITY - BOMB_PROBABILITY) + "%");
        System.out.println("[配置] 心跳周期: " + HEARTBEAT_INTERVAL_MS + "ms");
        System.out.println("[配置] 房间加入延迟: " + ROOM_JOIN_DELAY_MIN_MS + "~" + ROOM_JOIN_DELAY_MAX_MS + "ms");
        System.out.println("[配置] 对局重开延迟: " + GAME_RESTART_DELAY_MS + "ms");
        System.out.println();

        try {
            // 逐个连接所有虚拟客户端
            connectAllClients(group, scheduler);

            // 热身等待：所有客户端登录、加入房间、启动对局
            System.out.println("\n[热身] 等待客户端全部就绪...");
            waitForWarmup(roomCount);

            // 重置统计计数器到正式压测起点
            resetAllStats();

            // 启动进度报告 (每 30 秒)
            ScheduledExecutorService progressScheduler = Executors.newSingleThreadScheduledExecutor();
            progressScheduler.scheduleAtFixedRate(
                    () -> printSnapshot("实时统计"),
                    30, 30, TimeUnit.SECONDS
            );

            // 等待测试时长
            System.out.println("\n[进度] 正式压测进行中, 将持续 " + TEST_DURATION_SECONDS + " 秒...\n");
            Thread.sleep((long) TEST_DURATION_SECONDS * 1000);

            progressScheduler.shutdownNow();
        } catch (Exception e) {
            System.err.println("[错误] 测试异常: " + e.getMessage());
            e.printStackTrace();
        } finally {
            testEndTime = System.currentTimeMillis();

            scheduler.shutdownNow();
            group.shutdownGracefully(2, 5, TimeUnit.SECONDS);

            Thread.sleep(2000);
        }

        printFinalReport();
    }

    private static void connectAllClients(NioEventLoopGroup group, ScheduledExecutorService scheduler)
            throws InterruptedException {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, true);

        for (int i = 1; i <= CLIENT_COUNT; i++) {
            String username = String.format("stress_%06d", i);
            VirtualClientHandler handler = new VirtualClientHandler(i, username, scheduler);

            bootstrap.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ch.pipeline().addLast(
                            new LengthFieldBasedFrameDecoder(MAX_FRAME_LENGTH, 0, 4, 0, 4),
                            handler
                    );
                }
            });

            connectAttempts.increment();

            ChannelFuture future = bootstrap.connect(SERVER_HOST, SERVER_PORT);
            future.addListener((ChannelFutureListener) f -> {
                if (!f.isSuccess()) {
                    connectFailures.increment();
                }
            });

            if (i % 100 == 0) {
                System.out.println("[进度] 已发起 " + i + " / " + CLIENT_COUNT + " 个连接...");
            }
            Thread.sleep(CONNECT_RAMP_UP_INTERVAL_MS);
        }

        System.out.println("[进度] 所有 " + CLIENT_COUNT + " 个连接已发起，等待登录和入房...");
        Thread.sleep(3000);
    }

    /** 等待所有客户端完成登录并启动对局（热身阶段），最长等待 120 秒 */
    private static void waitForWarmup(int expectedRooms) throws InterruptedException {
        long warmupDeadline = System.currentTimeMillis() + 120_000;
        while (System.currentTimeMillis() < warmupDeadline) {
            int active = activeConnections.get();
            long started = gamesStarted.sum();
            if (active >= CLIENT_COUNT && started >= expectedRooms) {
                System.out.println("[热身] 完成! 活跃连接=" + active + ", 已启动对局=" + started);
                Thread.sleep(3000); // 额外等待 3 秒让消息流稳定
                return;
            }
            System.out.println("[热身] 等待中... 活跃连接=" + active + "/" + CLIENT_COUNT +
                    ", 已启动对局=" + started + "/" + expectedRooms);
            Thread.sleep(2000);
        }
        System.out.println("[警告] 热身超时(120s), 以当前状态开始正式压测");
    }

    /** 重置所有统计计数器，标记正式压测起点 */
    private static void resetAllStats() {
        testStartTime = System.currentTimeMillis();
        totalMessagesSent.reset();
        totalMessagesReceived.reset();
        totalErrors.reset();
        moveMessagesSent.reset();
        bombMessagesSent.reset();
        heartbeatMessagesSent.reset();
        roomCreates.reset();
        roomJoins.reset();
        gamesStarted.reset();
        gamesCompleted.reset();
        totalLatencyMicros.reset();
        latencyCount.reset();
        minLatencyMicros.set(Long.MAX_VALUE);
        maxLatencyMicros.set(0);
        System.out.println("[计时] 正式压测计时开始\n");
    }

    private static void printBanner() {
        System.out.println("  ╔══════════════════════════════════════════════╗");
        System.out.println("  ║    游戏服务器压力测试客户端 v2.0                 ║");
        System.out.println("  ║    Stress Test Client - Realistic Mode       ║");
        System.out.println("  ╚══════════════════════════════════════════════╝");
    }
}
