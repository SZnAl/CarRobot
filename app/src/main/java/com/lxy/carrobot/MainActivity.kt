package com.lxy.carrobot

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
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
import androidx.compose.animation.with
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.lxy.carrobot.ui.theme.CarRobotTheme
import kotlinx.coroutines.delay
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            CarRobotTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    BlinkingEyes()
                }
            }
        }
    }
}

/**
 * 定义机器人综合状态的数据类
 */
data class RobotState(
    val eyeDirection: EyeDirection = EyeDirection.CENTER,
    val rotationDirection: RotationDirection = RotationDirection.NONE,
    val movementDirection: MovementDirection = MovementDirection.NONE
)

@Composable
fun BlinkingEyes(modifier: Modifier = Modifier) {
    val blinkScale = remember { Animatable(1f) }

    val robotState = rememberRobotState()
    var randomLookDirection by remember { mutableStateOf(EyeDirection.CENTER) }

    var debugMode by remember { mutableStateOf(true) }
    var tapCount by remember { mutableStateOf(0) }
    var lastTapTs by remember { mutableStateOf(0L) }
    var debugRotationDirection by remember { mutableStateOf<RotationDirection?>(null) }
    var debugForward by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    // 眨眼动画循环
    LaunchedEffect(Unit) {
        val random = Random(System.currentTimeMillis())
        while (true) {
            val waitDuration = random.nextLong(1400L, 3200L)
            delay(waitDuration)
            blinkScale.animateTo(
                targetValue = 0.08f,
                animationSpec = tween(durationMillis = 120, easing = LinearEasing)
            )
            delay(50)
            blinkScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 160, easing = LinearEasing)
            )
        }
    }

    // 随机乱看逻辑
    LaunchedEffect(Unit) {
        val random = Random(System.currentTimeMillis() + 1)
        while (true) {
            delay(random.nextLong(3000L, 7000L))
            if (!debugMode &&
                robotState.rotationDirection == RotationDirection.NONE &&
                robotState.movementDirection == MovementDirection.NONE) {

                val dir = if (random.nextBoolean()) EyeDirection.LEFT else EyeDirection.RIGHT
                randomLookDirection = dir
                delay(random.nextLong(600L, 1200L))
                randomLookDirection = EyeDirection.CENTER
            }
        }
    }

    val scale = blinkScale.value.coerceIn(0.05f, 1f)

    val effectiveRotationDirection = debugRotationDirection ?: robotState.rotationDirection
    val effectiveMovementDirection = if (debugForward) MovementDirection.FORWARD else robotState.movementDirection

    // 眼睛朝向
    val activeDirection = when {
        debugRotationDirection != null -> when(debugRotationDirection) {
            RotationDirection.LEFT -> EyeDirection.LEFT
            RotationDirection.RIGHT -> EyeDirection.RIGHT
            else -> EyeDirection.CENTER
        }
        robotState.rotationDirection == RotationDirection.LEFT -> EyeDirection.LEFT
        robotState.rotationDirection == RotationDirection.RIGHT -> EyeDirection.RIGHT
        else -> randomLookDirection
    }

    // 计算眼睛位置动画
    val targetOffsets = when (effectiveRotationDirection) {
        RotationDirection.LEFT -> 120.dp to (-30).dp
        RotationDirection.RIGHT -> (-120).dp to (-30).dp
        RotationDirection.NONE -> 0.dp to 0.dp
    }
    val animatedOffsetX by animateDpAsState(
        targetValue = targetOffsets.first,
        animationSpec = tween(durationMillis = 600, easing = LinearEasing),
        label = "rowOffsetX"
    )
    val animatedOffsetY by animateDpAsState(
        targetValue = targetOffsets.second,
        animationSpec = tween(durationMillis = 600, easing = LinearEasing),
        label = "rowOffsetY"
    )
    val animatedPupilOffset by animateDpAsState(
        targetValue = activeDirection.offset,
        animationSpec = tween(durationMillis = 400, easing = LinearEasing),
        label = "pupilOffset"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { pos ->
                    val area = with(density) { 64.dp.toPx() }
                    val now = System.currentTimeMillis()
                    if (pos.x <= area && pos.y <= area) {
                        tapCount = if (now - lastTapTs <= 800) tapCount + 1 else 1
                        lastTapTs = now
                        if (tapCount >= 5) {
                            debugMode = true
                            tapCount = 0
                        }
                    }
                })
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedContent(
                targetState = effectiveMovementDirection,
                transitionSpec = {
                    fadeIn(animationSpec = tween(600)) togetherWith fadeOut(animationSpec = tween(600))
                },
                label = "ExpressionTransition"
            ) { targetState ->
                if (targetState == MovementDirection.FORWARD) {
                    ForwardExpression(scale = scale)
                } else {
                    Row(
                        modifier = Modifier.offset(x = animatedOffsetX, y = animatedOffsetY),
                        horizontalArrangement = Arrangement.spacedBy(56.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Eye(scaleY = scale, pupilOffset = animatedPupilOffset)
                        Eye(scaleY = scale, pupilOffset = animatedPupilOffset)
                    }
                }
            }
        }

        if (debugMode) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .zIndex(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ControlButton(label = "向左看") {
                    debugRotationDirection = RotationDirection.NONE
                    debugForward = false
                    randomLookDirection = EyeDirection.LEFT
                }
                ControlButton(label = "向右看") {
                    debugRotationDirection = RotationDirection.NONE
                    debugForward = false
                    randomLookDirection = EyeDirection.RIGHT
                }
                ControlButton(label = "前进") {
                    debugForward = true
                }
                ControlButton(label = "左转弯") {
                    debugRotationDirection = RotationDirection.LEFT
                    debugForward = false
                }
                ControlButton(label = "右转弯") {
                    debugRotationDirection = RotationDirection.RIGHT
                    debugForward = false
                }
                ControlButton(label = "退出调试") {
                    debugMode = false
                    debugRotationDirection = null
                    debugForward = false
                    randomLookDirection = EyeDirection.CENTER
                }
            }
        }
    }
}

@Composable
fun ControlButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick) {
        Text(text = label)
    }
}

@Composable
fun Eye(scaleY: Float, pupilOffset: Dp) {
    Box(
        modifier = Modifier
            .size(width = 140.dp, height = 200.dp)
            .graphicsLayer {
                this.scaleY = scaleY
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            }
            .background(Color.White, RoundedCornerShape(72.dp)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 80.dp, height = 140.dp)
                .offset(x = pupilOffset)
                .background(Color.Black, CircleShape)
        )
    }
}

enum class EyeDirection(val offset: Dp) {
    LEFT((-26).dp),
    CENTER(0.dp),
    RIGHT(26.dp)
}

enum class RotationDirection {
    LEFT,
    RIGHT,
    NONE
}

enum class MovementDirection {
    FORWARD,
    NONE
}

/**
 * 传感器逻辑
 */
@Composable
private fun rememberRobotState(): RobotState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(RobotState()) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val linearSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        // 转弯相关变量
        var currentRotation = RotationDirection.NONE
        // 转弯信心积分 (-30 ~ 30): 负数代表右转信心，正数代表左转信心
        var rotationConfidence = 0
        // 0.12 rad/s (约 7度/秒)，检测汽车的正常转弯
        val rotationThreshold = 0.1f
        // 触发门槛 需要积累多少信心才改变状态 (防抖动)
        val rotationTriggerLimit = 5

        // 前进相关变量
        var isMovingForward = false
        var lastMoveTimestamp = 0L
        var firstMoveTimestamp = 0L

        // 前进参数
        val forwardKeepAlive = 1500L
        val startDelay = 6000L

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val now = System.currentTimeMillis()

                if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                    // 陀螺仪逻辑优化
                    val x = event.values[0]
                    val y = event.values[1]

                    // 判断瞬时旋转方向
                    var instantDir = 0 // 0:无, 1:左, -1:右

                    // 检测逻辑：检查 Y 轴或 X 轴是否超过低阈值
                    if (y > rotationThreshold || x < -rotationThreshold) {
                        instantDir = 1 // 瞬间向左趋势
                    } else if (y < -rotationThreshold || x > rotationThreshold) {
                        instantDir = -1 // 瞬间向右趋势
                    }

                    // 信心积分逻辑 滤波
                    if (instantDir == 1) {
                        // 正在向左转，增加左转信心
                        if (rotationConfidence < 20) rotationConfidence++
                    } else if (instantDir == -1) {
                        // 正在向右转，增加右转信心 (负数)
                        if (rotationConfidence > -20) rotationConfidence--
                    } else {
                        // 没有明显旋转，信心归零 (衰减速度快一点，让停止转弯反应灵敏)
                        if (rotationConfidence > 0) rotationConfidence -= 2
                        if (rotationConfidence < 0) rotationConfidence += 2
                    }

                    // 归零修正
                    if (rotationConfidence in -1..1 && instantDir == 0) rotationConfidence = 0

                    // 根据信心值判定最终状态
                    currentRotation = when {
                        rotationConfidence >= rotationTriggerLimit -> RotationDirection.RIGHT
                        rotationConfidence <= -rotationTriggerLimit -> RotationDirection.LEFT
                        else -> RotationDirection.NONE
                    }

                } else if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
                    // 加速度逻辑
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    val magnitude = kotlin.math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                    val moveThreshold = 1.5f

                    if (magnitude > moveThreshold) {
                        lastMoveTimestamp = now
                        if (firstMoveTimestamp == 0L) {
                            firstMoveTimestamp = now
                        }
                        if (now - firstMoveTimestamp > startDelay) {
                            isMovingForward = true
                        }
                    } else {
                        if (now - lastMoveTimestamp > forwardKeepAlive) {
                            isMovingForward = false
                            firstMoveTimestamp = 0L
                        }
                    }
                }

                val finalMovement = if (currentRotation != RotationDirection.NONE) {
                    // 如果检测到确实在转弯，立即打断前进判定
                    firstMoveTimestamp = 0L
                    isMovingForward = false
                    MovementDirection.NONE
                } else if (isMovingForward) {
                    MovementDirection.FORWARD
                } else {
                    MovementDirection.NONE
                }

                val targetEyeDir = when(currentRotation) {
                    RotationDirection.LEFT -> EyeDirection.LEFT
                    RotationDirection.RIGHT -> EyeDirection.RIGHT
                    else -> EyeDirection.CENTER
                }

                state = RobotState(
                    eyeDirection = targetEyeDir,
                    rotationDirection = currentRotation,
                    movementDirection = finalMovement
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }
        }

        if (linearSensor != null && gyroSensor != null) {
            sensorManager.registerListener(listener, linearSensor, SensorManager.SENSOR_DELAY_GAME)
            sensorManager.registerListener(listener, gyroSensor, SensorManager.SENSOR_DELAY_GAME)
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    return state
}

@Composable
private fun ForwardExpression(scale: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "head_sway")
    val swayRotation by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotation"
    )

    Box(
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.graphicsLayer {
                rotationZ = swayRotation
                transformOrigin = TransformOrigin.Center
            }
        ) {
            Eye(scaleY = scale, pupilOffset = 0.dp)
            Eye(scaleY = scale, pupilOffset = 0.dp)
        }

        WavingFlag(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = (-183).dp, y = 125.dp)
                .size(60.dp)
        )
    }
}

@Composable
fun WavingFlag(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "flag_wave")
    val rotation by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 300, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flag_rotation"
    )

    Canvas(modifier = modifier.graphicsLayer {
        rotationZ = rotation
        transformOrigin = TransformOrigin(0.2f, 1f)
    }) {
        val width = size.width
        val height = size.height

        drawLine(
            color = Color.White,
            start = Offset(x = width * 0.2f, y = height),
            end = Offset(x = width * 0.2f, y = height * 0.1f),
            strokeWidth = 6f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )

        val flagStartX = width * 0.2f
        val flagStartY = height * 0.1f
        val flagSize = width * 0.6f
        val cellSize = flagSize / 2

        drawRect(
            color = Color.White,
            topLeft = Offset(flagStartX, flagStartY),
            size = Size(flagSize, flagSize),
            style = Stroke(width = 4f)
        )

        drawRect(
            color = Color.White,
            topLeft = Offset(flagStartX, flagStartY),
            size = Size(cellSize, cellSize)
        )
        drawRect(
            color = Color.White,
            topLeft = Offset(flagStartX + cellSize, flagStartY + cellSize),
            size = Size(cellSize, cellSize)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BlinkingEyesPreview() {
    CarRobotTheme {
        BlinkingEyes(
            modifier = Modifier.fillMaxSize()
        )
    }
}