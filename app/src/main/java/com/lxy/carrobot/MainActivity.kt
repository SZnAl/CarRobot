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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun BlinkingEyes(modifier: Modifier = Modifier) {
    val blinkScale = remember { Animatable(1f) }
    var userDirection by remember { mutableStateOf(EyeDirection.CENTER) }
    val rotationDirection = rememberRotationDirection()
    val movementDirection = rememberMovementDirection()
    var debugMode by remember { mutableStateOf(false) }
    var tapCount by remember { mutableStateOf(0) }
    var lastTapTs by remember { mutableStateOf(0L) }
    var debugRotationDirection by remember { mutableStateOf<RotationDirection?>(null) }
    var debugForward by remember { mutableStateOf(false) }
    val density = LocalDensity.current

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

    LaunchedEffect(Unit) {
        val random = Random(System.currentTimeMillis() + 1)
        while (true) {
            delay(random.nextLong(3000L, 7000L))
            if (!debugMode && rotationDirection == RotationDirection.NONE && userDirection == EyeDirection.CENTER) {
                val dir = if (random.nextBoolean()) EyeDirection.LEFT else EyeDirection.RIGHT
                userDirection = dir
                delay(random.nextLong(600L, 1200L))
                userDirection = EyeDirection.CENTER
            }
        }
    }

    val scale = blinkScale.value.coerceIn(0.05f, 1f)
    val effectiveRotationDirection = debugRotationDirection ?: rotationDirection
    val activeDirection = when (effectiveRotationDirection) {
        RotationDirection.LEFT -> EyeDirection.LEFT
        RotationDirection.RIGHT -> EyeDirection.RIGHT
        RotationDirection.NONE -> userDirection
    }
    val targetOffsets = when (effectiveRotationDirection) {
        RotationDirection.LEFT -> 120.dp to (-30).dp
        RotationDirection.RIGHT -> (-120).dp to (-30).dp
        RotationDirection.NONE -> 0.dp to 0.dp
    }
    val animatedOffsetX by animateDpAsState(
        targetValue = targetOffsets.first,
        animationSpec = tween(durationMillis = 320, easing = LinearEasing),
        label = "rowOffsetX"
    )
    val animatedOffsetY by animateDpAsState(
        targetValue = targetOffsets.second,
        animationSpec = tween(durationMillis = 320, easing = LinearEasing),
        label = "rowOffsetY"
    )
    val animatedPupilOffset by animateDpAsState(
        targetValue = activeDirection.offset,
        animationSpec = tween(durationMillis = 250, easing = LinearEasing),
        label = "pupilOffset"
    )

    val effectiveMovementDirection = if (debugForward) MovementDirection.FORWARD else movementDirection

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
            if (effectiveMovementDirection == MovementDirection.FORWARD) {
                ForwardExpression()
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
                    userDirection = EyeDirection.LEFT
                }
                ControlButton(label = "向右看") {
                    debugRotationDirection = RotationDirection.NONE
                    debugForward = false
                    userDirection = EyeDirection.RIGHT
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
                ControlButton(label = "退出调试模式") {
                    debugMode = false
                    debugRotationDirection = null
                    debugForward = false
                    userDirection = EyeDirection.CENTER
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
            .size(width = 140.dp, height = 220.dp)
            .graphicsLayer {
                this.scaleY = scaleY
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            }
            .background(Color.White, RoundedCornerShape(72.dp)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                // .size(70.dp)
                .size(width = 80.dp, height = 160.dp)
                .offset(x = pupilOffset)
                .background(Color.Black, CircleShape)
        )
    }
}

private enum class EyeDirection(val offset: Dp) {
    LEFT((-26).dp),
    CENTER(0.dp),
    RIGHT(26.dp)
}

private enum class RotationDirection {
    LEFT,
    RIGHT,
    NONE
}

private enum class MovementDirection {
    FORWARD,
    NONE
}

@Composable
private fun rememberRotationDirection(): RotationDirection {
    val context = LocalContext.current
    var rotationDirection by remember { mutableStateOf(RotationDirection.NONE) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val z = event.values[2]
                val threshold = 0.35f
                rotationDirection = when {
                    z > threshold -> RotationDirection.LEFT
                    z < -threshold -> RotationDirection.RIGHT
                    else -> RotationDirection.NONE
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }
        }

        if (gyroSensor != null) {
            sensorManager.registerListener(listener, gyroSensor, SensorManager.SENSOR_DELAY_GAME)
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    return rotationDirection
}

@Composable
private fun rememberMovementDirection(): MovementDirection {
    val context = LocalContext.current
    var movementDirection by remember { mutableStateOf(MovementDirection.NONE) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val linear = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val values = event.values
                val ax = values[0]
                val ay = values[1]
                val threshold = 1.2f
                movementDirection = if (kotlin.math.max(kotlin.math.abs(ax), kotlin.math.abs(ay)) > threshold) MovementDirection.FORWARD else MovementDirection.NONE
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }
        }

        val sensor = linear ?: accel
        if (sensor != null) {
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    return movementDirection
}

@Composable
private fun ForwardExpression() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "0", color = Color.White, fontSize = 96.sp)
        Text(text = "0", color = Color.White, fontSize = 96.sp)
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
