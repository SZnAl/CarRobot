package com.lxy.carrobot

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import com.lxy.carrobot.ui.theme.CarRobotTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            CarRobotTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    BlinkingEyes()
                }
            }
        }
    }
}

// 定义机器人的各种状态枚举
enum class AppMode { NORMAL, FORWARD, MUSIC }
enum class EyeDirection(val offset: Dp) { LEFT((-26).dp), CENTER(0.dp), RIGHT(26.dp) }
enum class RotationDirection { LEFT, RIGHT, NONE }
enum class MovementDirection { FORWARD, NONE }

// 待机时的随机动作枚举
enum class IdleAction {
    NORMAL_LOOK, // 普通睁眼（看左/看右/看中）
    HEARTS,      // 爱心眼
    STARS,       // 星星眼
    CHEERING,    // 欢呼（手）
    WHISTLING,   // 吹口哨/亲亲
    BUTTERFLY    // 蝴蝶
}

data class RobotState(
    val eyeDirection: EyeDirection = EyeDirection.CENTER,
    val rotationDirection: RotationDirection = RotationDirection.NONE,
    val movementDirection: MovementDirection = MovementDirection.NONE,
    val isListeningMusic: Boolean = false
)

@Composable
fun BlinkingEyes(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val blinkScale = remember { Animatable(1f) }

    // 权限请求
    var hasAudioPermission by remember {
        mutableStateOf(ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasAudioPermission = it }

    LaunchedEffect(Unit) {
        if (!hasAudioPermission) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // 获取传感器和音频状态
    val robotState = rememberRobotState(hasAudioPermission)

    // 待机状态管理
    var idleAction by remember { mutableStateOf(IdleAction.NORMAL_LOOK) }
    var randomLookDirection by remember { mutableStateOf(EyeDirection.CENTER) }

    var debugMode by remember { mutableStateOf(true) }
    var tapCount by remember { mutableStateOf(0) }
    var lastTapTs by remember { mutableStateOf(0L) }

    // 调试变量
    var debugRotationDirection by remember { mutableStateOf<RotationDirection?>(null) }
    var debugForward by remember { mutableStateOf(false) }
    var debugMusic by remember { mutableStateOf(false) }

    // 调试特定的待机表情
    var debugIdleAction by remember { mutableStateOf<IdleAction?>(null) }

    val density = LocalDensity.current

    // 眨眼动画 (仅在普通睁眼模式下生效)
    LaunchedEffect(Unit) {
        val random = Random(System.currentTimeMillis())
        while (true) {
            val waitDuration = random.nextLong(1400L, 3200L)
            delay(waitDuration)
            blinkScale.animateTo(0.08f, tween(120, easing = LinearEasing))
            delay(50)
            blinkScale.animateTo(1f, tween(160, easing = LinearEasing))
        }
    }

    // 待机循环：在普通看、特殊表情之间随机切换
    LaunchedEffect(Unit) {
        val random = Random(System.currentTimeMillis() + 1)
        while (true) {
            // 基础等待时间 (普通发呆乱看的时间)
            delay(random.nextLong(2000L, 5000L))

            // 只有在非强制模式（无传感器、无音乐、无调试）下才进行随机切换
            if (!debugMode &&
                robotState.rotationDirection == RotationDirection.NONE &&
                robotState.movementDirection == MovementDirection.NONE &&
                !robotState.isListeningMusic) {

                // 35% 概率出特殊表情
                val roll = random.nextInt(100)

                if (roll < 35) {
                    // 切换前先回正 先让眼睛回到中间，给用户一个心理准备，过渡更自然
                    randomLookDirection = EyeDirection.CENTER
                    delay(600) // 等待眼球回正动画完成

                    // 随机选择一个特殊表情
                    val specials = listOf(IdleAction.HEARTS, IdleAction.STARS, IdleAction.CHEERING, IdleAction.WHISTLING, IdleAction.BUTTERFLY)
                    idleAction = specials.random()

                    // 特殊表情展示时间 6~10秒
                    delay(random.nextLong(6000L, 10000L))

                    // 变回普通状态
                    idleAction = IdleAction.NORMAL_LOOK
                } else {
                    // 普通乱看逻辑
                    idleAction = IdleAction.NORMAL_LOOK
                    val dir = if (random.nextBoolean()) EyeDirection.LEFT else EyeDirection.RIGHT
                    randomLookDirection = dir
                    // 乱看维持时间
                    delay(random.nextLong(1500L, 3000L))
                    // 看完回正
                    randomLookDirection = EyeDirection.CENTER
                    delay(1000) // 回正后停顿一下
                }
            }
        }
    }

    val scale = blinkScale.value.coerceIn(0.05f, 1f)

    // 状态合成
    val effectiveRotation = debugRotationDirection ?: robotState.rotationDirection
    val effectiveForward = if (debugForward) true else robotState.movementDirection == MovementDirection.FORWARD
    val effectiveMusic = if (debugMusic) true else robotState.isListeningMusic

    // 最终的待机动作（调试优先）
    val effectiveIdleAction = debugIdleAction ?: idleAction

    val currentMode = when {
        effectiveMusic -> AppMode.MUSIC
        effectiveRotation != RotationDirection.NONE -> AppMode.NORMAL // 转弯时强制切回普通眼睛
        effectiveForward -> AppMode.FORWARD
        else -> AppMode.NORMAL
    }

    // 眼睛方向
    val activeDirection = when {
        effectiveRotation == RotationDirection.LEFT -> EyeDirection.LEFT
        effectiveRotation == RotationDirection.RIGHT -> EyeDirection.RIGHT
        else -> randomLookDirection
    }

    // 状态文本
    val statusText = when (currentMode) {
        AppMode.MUSIC -> "状态：听音乐 \uD83C\uDFB8"
        AppMode.FORWARD -> "状态：前进"
        AppMode.NORMAL -> {
            if (effectiveRotation != RotationDirection.NONE) {
                if (effectiveRotation == RotationDirection.LEFT) "状态：左转" else "状态：右转"
            } else {
                // 显示当前的待机表情名称
                when(effectiveIdleAction) {
                    IdleAction.NORMAL_LOOK -> "状态：发呆"
                    IdleAction.HEARTS -> "状态：爱心"
                    IdleAction.STARS -> "状态：星星"
                    IdleAction.CHEERING -> "状态：欢呼"
                    IdleAction.WHISTLING -> "状态：口哨"
                    IdleAction.BUTTERFLY -> "状态：蝴蝶"
                }
            }
        }
    }

    // 动画偏移量
    val targetOffsets = when (effectiveRotation) {
        RotationDirection.LEFT -> 120.dp to (-30).dp
        RotationDirection.RIGHT -> (-120).dp to (-30).dp
        RotationDirection.NONE -> 0.dp to 0.dp
    }
    val animatedOffsetX by animateDpAsState(targetOffsets.first, tween(600), label = "x")
    val animatedOffsetY by animateDpAsState(targetOffsets.second, tween(600), label = "y")
    val animatedPupilOffset by animateDpAsState(activeDirection.offset, tween(400), label = "pupil")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { pos ->
                    val area = with(density) { 64.dp.toPx() }
                    if (pos.x <= area && pos.y <= area) {
                        val now = System.currentTimeMillis()
                        tapCount = if (now - lastTapTs <= 800) tapCount + 1 else 1
                        lastTapTs = now
                        if (tapCount >= 5) { debugMode = true; tapCount = 0 }
                    }
                })
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedContent(
                targetState = currentMode,
                transitionSpec = {
                    (fadeIn(tween(1000)) + scaleIn(initialScale = 0.95f, animationSpec = tween(1000, easing = FastOutSlowInEasing)))
                        .togetherWith(
                            fadeOut(tween(1000)) + scaleOut(targetScale = 0.95f, animationSpec = tween(1000, easing = FastOutSlowInEasing))
                        )
                },
                label = "ModeTransition"
            ) { mode ->
                when (mode) {
                    AppMode.MUSIC -> MusicExpression()
                    AppMode.FORWARD -> ForwardExpression(scale = scale)
                    AppMode.NORMAL -> {
                        // 在 Normal 模式下，根据 IdleAction 切换内容
                        AnimatedContent(
                            targetState = effectiveIdleAction,
                            transitionSpec = {
                                // 入场：从 90% 大小放大到 100%，且淡入
                                (fadeIn(animationSpec = tween(1000, easing = LinearEasing)) +
                                        scaleIn(initialScale = 0.9f, animationSpec = tween(1000, easing = FastOutSlowInEasing)))
                                    .togetherWith(
                                        // 出场：缩小到 90% 大小，且淡出
                                        fadeOut(animationSpec = tween(1000, easing = LinearEasing)) +
                                                scaleOut(targetScale = 0.9f, animationSpec = tween(1000, easing = FastOutSlowInEasing))
                                    )
                            },
                            label = "IdleSwitch"
                        ) { action ->
                            if (action == IdleAction.NORMAL_LOOK || effectiveRotation != RotationDirection.NONE) {
                                // 普通眼睛
                                Row(
                                    modifier = Modifier.offset(x = animatedOffsetX, y = animatedOffsetY),
                                    horizontalArrangement = Arrangement.spacedBy(56.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Eye(scaleY = scale, pupilOffset = animatedPupilOffset)
                                    Eye(scaleY = scale, pupilOffset = animatedPupilOffset)
                                }
                            } else {
                                // 特殊表情
                                SpecialIdleExpression(action)
                            }
                        }
                    }
                }
            }
        }

        if (debugMode) {
            val resetDebug = {
                debugRotationDirection = null
                debugForward = false
                debugMusic = false
                debugIdleAction = null
                randomLookDirection = EyeDirection.CENTER
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 32.dp, end = 32.dp)
                    .background(Color.DarkGray.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(statusText, color = Color.Green, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 12.dp).zIndex(1f)
            ) {
                // 第一行：基础动作
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ControlButton("左看") { resetDebug(); randomLookDirection = EyeDirection.LEFT }
                    ControlButton("右看") { resetDebug(); randomLookDirection = EyeDirection.RIGHT }
                    ControlButton("听音乐") { resetDebug(); debugMusic = true }
                }
                // 第二行：传感器模拟
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ControlButton("前进") { resetDebug(); debugForward = true }
                    ControlButton("左转") { resetDebug(); debugRotationDirection = RotationDirection.LEFT }
                    ControlButton("右转") { resetDebug(); debugRotationDirection = RotationDirection.RIGHT }
                    ControlButton("退出") { resetDebug(); debugMode = false }
                }
                // 第三行：特殊表情测试
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ControlButton("爱心") { resetDebug(); debugIdleAction = IdleAction.HEARTS }
                    ControlButton("星星") { resetDebug(); debugIdleAction = IdleAction.STARS }
                    ControlButton("欢呼") { resetDebug(); debugIdleAction = IdleAction.CHEERING }
                    ControlButton("口哨") { resetDebug(); debugIdleAction = IdleAction.WHISTLING }
                    ControlButton("蝴蝶") { resetDebug(); debugIdleAction = IdleAction.BUTTERFLY }
                }
            }
        }
    }
}

@Composable
fun ControlButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.padding(1.dp).size(width = 70.dp, height = 38.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
    ) {
        Text(text = label, fontSize = 11.sp)
    }
}

//  表情绘制组件

@Composable
fun Eye(scaleY: Float, pupilOffset: Dp) {
    Box(
        modifier = Modifier.size(140.dp, 200.dp).graphicsLayer { this.scaleY = scaleY; transformOrigin = TransformOrigin(0.5f, 0.5f) }
            .background(Color.White, RoundedCornerShape(72.dp)),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.size(80.dp, 140.dp).offset(x = pupilOffset).background(Color.Black, CircleShape))
    }
}

@Composable
fun HappyEye() {
    Canvas(modifier = Modifier.size(width = 100.dp, height = 60.dp)) {
        val strokeWidth = 12.dp.toPx()
        val path = Path().apply {
            moveTo(0f, size.height)
            quadraticBezierTo(size.width / 2, -size.height * 0.5f, size.width, size.height)
        }
        drawPath(path, Color.White, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
    }
}

@Composable
fun SpecialIdleExpression(action: IdleAction) {
    // 待机表情统一布局：两只笑眼 + 中间下方的图标
    Box(contentAlignment = Alignment.Center) {
        // 眼睛部分
        Row(
            horizontalArrangement = Arrangement.spacedBy(100.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.offset(y = (-40).dp) // 稍微上移，给嘴巴留空间
        ) {
            HappyEye()
            HappyEye()
        }

        // 图标部分
        val iconModifier = Modifier.align(Alignment.Center).offset(y = 60.dp) // 放在眼睛下方

        when(action) {
            IdleAction.HEARTS -> IconHearts(iconModifier.offset(x=60.dp, y=(-60).dp)) // 爱心通常在旁边
            IdleAction.STARS -> IconStars(iconModifier.offset(y=(-80).dp)) // 星星在中间或额头
            IdleAction.CHEERING -> IconCheering(iconModifier.offset(y=80.dp)) // 手在下面
            IdleAction.WHISTLING -> IconWhistling(iconModifier)
            IdleAction.BUTTERFLY -> IconButterfly(iconModifier.offset(x=(-70).dp, y=20.dp))
            else -> {}
        }
    }
}

//  动态图标绘制

@Composable
fun IconHearts(modifier: Modifier) {
    // 动画：心跳效果
    val infiniteTransition = rememberInfiniteTransition(label = "heart_beat")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "scale"
    )

    Canvas(modifier = modifier.size(80.dp)) {
        val path = Path()
        fun drawHeart(heartScale: Float, offset: Offset) {
            val w = 30f * heartScale
            val h = 30f * heartScale
            path.reset()
            path.moveTo(offset.x + w/2, offset.y + h/5)
            path.cubicTo(offset.x + w/2, offset.y - h/4, offset.x, offset.y + h/10, offset.x + w/2, offset.y + h)
            path.cubicTo(offset.x + w, offset.y + h/10, offset.x + w/2, offset.y - h/4, offset.x + w/2, offset.y + h/5)
            drawPath(path, Color.White, style = Stroke(width = 4f.dp.toPx(), cap = StrokeCap.Round))
        }

        // 让三个爱心跟随心跳节奏缩放
        // 主爱心
        drawHeart(1.0f * scale, Offset(0f, 20f))
        // 右上角小爱心 (相位稍微错开一点，这里简单处理同步跳动)
        drawHeart(0.7f * scale, Offset(50f, 0f))
        // 下方小爱心
        drawHeart(0.5f * scale, Offset(40f, 60f))
    }
}

@Composable
fun IconStars(modifier: Modifier) {
    // 星星闪烁 (旋转 + 缩放)
    val infiniteTransition = rememberInfiniteTransition(label = "star_twinkle")
    val rotation by infiniteTransition.animateFloat(
        initialValue = -15f, targetValue = 15f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
        label = "rotation"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
        label = "scale"
    )

    Canvas(modifier = modifier.size(100.dp)) {
        fun drawStar(center: Offset, size: Float, rotateDeg: Float) {
            // 对每个星星进行独立的旋转和缩放变换
            rotate(rotateDeg, pivot = center) {
                scale(scale, pivot = center) {
                    val s = size
                    val p = Path()
                    p.moveTo(center.x, center.y - s); p.lineTo(center.x, center.y + s)
                    p.moveTo(center.x - s, center.y); p.lineTo(center.x + s, center.y)
                    drawPath(p, Color.White, style = Stroke(width = 3f.dp.toPx(), cap = StrokeCap.Round))

                    val diag = s * 0.6f
                    drawLine(Color.White, Offset(center.x-diag, center.y-diag), Offset(center.x+diag, center.y+diag), strokeWidth = 3f.dp.toPx())
                    drawLine(Color.White, Offset(center.x-diag, center.y+diag), Offset(center.x+diag, center.y-diag), strokeWidth = 3f.dp.toPx())
                }
            }
        }

        // 主星星旋转
        drawStar(center, 40f, rotation)
        // 旁边的小星星反向旋转，增加动感
        drawStar(Offset(center.x - 50f, center.y + 20f), 20f, -rotation * 1.5f)
        drawStar(Offset(center.x + 50f, center.y - 30f), 25f, -rotation)
    }
}

@Composable
fun IconCheering(modifier: Modifier) {
    // 手部摇摆 (打Call)
    val infiniteTransition = rememberInfiniteTransition(label = "cheer_wave")
    val waveAngle by infiniteTransition.animateFloat(
        initialValue = -10f, targetValue = 20f,
        animationSpec = infiniteRepeatable(tween(400, easing = LinearEasing), RepeatMode.Reverse),
        label = "angle"
    )

    Canvas(modifier = modifier.size(120.dp, 80.dp)) {
        // 左手摇摆
        rotate(waveAngle - 10f, pivot = Offset(size.width*0.3f, size.height)) {
            drawRoundRect(Color.White, topLeft = Offset(0f, 20f), size = Size(30f, 80f), cornerRadius = CornerRadius(10f))
        }
        // 右手反向/同步摇摆
        rotate(-waveAngle + 10f, pivot = Offset(size.width*0.7f, size.height)) {
            drawRoundRect(Color.White, topLeft = Offset(size.width - 30f, 20f), size = Size(30f, 80f), cornerRadius = CornerRadius(10f))
        }
    }
}

@Composable
fun IconWhistling(modifier: Modifier) {
    // 音符漂浮 & 嘴巴缩放
    val infiniteTransition = rememberInfiniteTransition(label = "whistle")
    val noteOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -15f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label = "note_y"
    )
    val mouthScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(400, easing = LinearEasing), RepeatMode.Reverse),
        label = "mouth_scale"
    )

    Canvas(modifier = modifier.size(60.dp)) {
        // 嘴巴 (根据节奏缩放)
        scale(mouthScale, pivot = center) {
            drawCircle(Color.White, style = Stroke(width = 5f.dp.toPx()))
        }

        // 音符 (上下漂浮)
        translate(top = noteOffset) {
            val notePath = Path().apply {
                moveTo(80f, -20f)
                lineTo(80f, 40f)
                addOval(androidx.compose.ui.geometry.Rect(60f, 30f, 80f, 50f))
                moveTo(80f, -20f)
                lineTo(100f, -10f)
            }
            drawPath(notePath, Color.White, style = Stroke(width = 3f.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
fun IconButterfly(modifier: Modifier) {
    // 翅膀扇动 (X轴缩放) + 上下飞舞 (Y轴位移)
    val infiniteTransition = rememberInfiniteTransition(label = "butterfly")
    // 翅膀开合：X轴从 1f 变到 0.3f 模拟合拢
    val wingFlap by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(150, easing = LinearEasing), RepeatMode.Reverse),
        label = "flap"
    )
    // 身体悬停：上下缓慢移动
    val hoverY by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -20f,
        animationSpec = infiniteRepeatable(tween(1000, easing = androidx.compose.animation.core.FastOutSlowInEasing), RepeatMode.Reverse),
        label = "hover"
    )

    Canvas(modifier = modifier.size(60.dp).graphicsLayer {
        translationY = hoverY
        // 整体旋转一点角度，让飞行姿态更自然
        rotationZ = -15f
    }) {
        // 使用 scale 变换来实现翅膀扇动效果
        scale(scaleX = wingFlap, scaleY = 1f, pivot = center) {
            val path = Path()
            path.moveTo(center.x, center.y)
            // 左翅膀
            path.cubicTo(center.x-40f, center.y-40f, center.x-60f, center.y+10f, center.x, center.y)
            path.cubicTo(center.x-50f, center.y+40f, center.x-20f, center.y+60f, center.x, center.y+10f)
            // 右翅膀
            path.cubicTo(center.x+40f, center.y-40f, center.x+60f, center.y+10f, center.x, center.y)
            drawPath(path, Color.White, style = Stroke(width = 3f.dp.toPx()))
        }
    }
}

@Composable
private fun ForwardExpression(scale: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "head_sway")
    val swayRotation by infiniteTransition.animateFloat(
        initialValue = -8f, targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
        label = "rotation"
    )

    Box(contentAlignment = Alignment.Center) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.graphicsLayer { rotationZ = swayRotation; transformOrigin = TransformOrigin.Center }
        ) {
            Eye(scaleY = scale, pupilOffset = 0.dp)
            Eye(scaleY = scale, pupilOffset = 0.dp)
        }
        WavingFlag(modifier = Modifier.align(Alignment.Center).offset(x = (-183).dp, y = 125.dp).size(60.dp))
    }
}

@Composable
private fun MusicExpression() {
    val infiniteTransition = rememberInfiniteTransition(label = "music_bop")
    val scale by infiniteTransition.animateFloat(0.95f, 1.05f, infiniteRepeatable(tween(400), RepeatMode.Reverse))
    val rotation by infiniteTransition.animateFloat(-3f, 3f, infiniteRepeatable(tween(400), RepeatMode.Reverse))

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale; rotationZ = rotation }) {
            Row(horizontalArrangement = Arrangement.spacedBy(100.dp)) { HappyEye(); HappyEye() }
            GuitarIcon(modifier = Modifier.align(Alignment.Center).offset(y = 80.dp).size(100.dp))
        }
    }
}

@Composable
fun GuitarIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        rotate(-30f, pivot = center) {
            val w = size.width; val h = size.height
            drawRoundRect(Color(0xFF8D6E63), Offset(w*0.42f, h*0.1f), Size(w*0.16f, h*0.6f), CornerRadius(5f))
            drawRoundRect(Color(0xFF5D4037), Offset(w*0.40f, 0f), Size(w*0.20f, h*0.15f), CornerRadius(5f))
            drawCircle(Color(0xFFFFAB91), w*0.3f, Offset(w*0.5f, h*0.75f))
            drawCircle(Color(0xFFFFAB91), w*0.22f, Offset(w*0.5f, h*0.55f))
            drawCircle(Color(0xFF3E2723), w*0.1f, Offset(w*0.5f, h*0.55f))
            drawLine(Color.White, Offset(w*0.5f, h*0.1f), Offset(w*0.5f, h*0.8f), 3f)
        }
    }
}

@Composable
fun WavingFlag(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition("flag")
    val rotation by infiniteTransition.animateFloat(-10f, 15f, infiniteRepeatable(tween(300), RepeatMode.Reverse))
    Canvas(modifier.graphicsLayer { rotationZ = rotation; transformOrigin = TransformOrigin(0.2f, 1f) }) {
        val w = size.width; val h = size.height
        drawLine(Color.White, Offset(w*0.2f, h), Offset(w*0.2f, h*0.1f), 6f, StrokeCap.Round)
        drawRect(Color.White, Offset(w*0.2f, h*0.1f), Size(w*0.6f, w*0.6f), style = Stroke(4f))
        val cs = w*0.3f
        drawRect(Color.White, Offset(w*0.2f, h*0.1f), Size(cs, cs))
        drawRect(Color.White, Offset(w*0.2f+cs, h*0.1f+cs), Size(cs, cs))
    }
}

// 传感器逻辑
@Composable
private fun rememberRobotState(hasAudioPermission: Boolean): RobotState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(RobotState()) }
    val scope = rememberCoroutineScope()

    DisposableEffect(context, hasAudioPermission) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val linearSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        var currentRotation = RotationDirection.NONE
        var rotationConfidence = 0
        var isMovingForward = false
        var lastMoveTimestamp = 0L; var firstMoveTimestamp = 0L
        var isMusicPlaying = false; var musicConfidence = 0
        var audioRecord: AudioRecord? = null; var isRecording = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val now = System.currentTimeMillis()
                if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                    val y = event.values[1]
                    val threshold = 0.12f
                    var dir = 0
                    if (y > threshold) dir = 1 else if (y < -threshold) dir = -1

                    if (dir == 1) { if(rotationConfidence < 20) rotationConfidence++ }
                    else if (dir == -1) { if(rotationConfidence > -20) rotationConfidence-- }
                    else { if(rotationConfidence > 0) rotationConfidence -= 2; if(rotationConfidence < 0) rotationConfidence += 2 }

                    currentRotation = when {
                        rotationConfidence >= 5 -> RotationDirection.LEFT
                        rotationConfidence <= -5 -> RotationDirection.RIGHT
                        else -> RotationDirection.NONE
                    }
                } else if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
                    val mag = sqrt(event.values[0]*event.values[0] + event.values[1]*event.values[1] + event.values[2]*event.values[2])
                    if (mag > 1.5f) {
                        lastMoveTimestamp = now
                        if (firstMoveTimestamp == 0L) firstMoveTimestamp = now
                        if (now - firstMoveTimestamp > 6000L) isMovingForward = true
                    } else {
                        if (now - lastMoveTimestamp > 1500L) { isMovingForward = false; firstMoveTimestamp = 0L }
                    }
                }
                updateState()
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}

            fun updateState() {
                val finalMov = if (currentRotation != RotationDirection.NONE || isMusicPlaying) {
                    firstMoveTimestamp = 0L; isMovingForward = false; MovementDirection.NONE
                } else if (isMovingForward) MovementDirection.FORWARD else MovementDirection.NONE

                val finalRot = if (isMusicPlaying) RotationDirection.NONE else currentRotation
                val dir = when(finalRot) { RotationDirection.LEFT->EyeDirection.LEFT; RotationDirection.RIGHT->EyeDirection.RIGHT; else->EyeDirection.CENTER }
                state = RobotState(dir, finalRot, finalMov, isMusicPlaying)
            }
        }

        if (linearSensor != null && gyroSensor != null) {
            sensorManager.registerListener(listener, linearSensor, SensorManager.SENSOR_DELAY_GAME)
            sensorManager.registerListener(listener, gyroSensor, SensorManager.SENSOR_DELAY_GAME)
        }

        if (hasAudioPermission) {
            scope.launch(Dispatchers.IO) {
                try {
                    val bufferSize = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, 44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
                        val buffer = ShortArray(bufferSize)
                        audioRecord?.startRecording()
                        isRecording = true
                        while (isRecording) {
                            val read = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                            if (read > 0) {
                                var sum = 0.0
                                for (i in 0 until read) sum += buffer[i] * buffer[i]
                                val amp = sqrt(sum / read)
                                if (amp > 2000) { if(musicConfidence < 30) musicConfidence++ }
                                else { if(musicConfidence > 0) musicConfidence -= 2 }
                                isMusicPlaying = musicConfidence > 15
                            }
                            delay(50)
                        }
                    }
                } catch (e: Exception) { Log.e("Audio", "Error", e) }
            }
        }
        onDispose {
            sensorManager.unregisterListener(listener)
            isRecording = false
            try { audioRecord?.stop(); audioRecord?.release() } catch (e: Exception) {}
        }
    }
    return state
}