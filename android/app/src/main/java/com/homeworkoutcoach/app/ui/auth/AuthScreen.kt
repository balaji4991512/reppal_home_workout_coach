package com.homeworkoutcoach.app.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.homeworkoutcoach.app.ui.components.Body
import com.homeworkoutcoach.app.ui.components.H1
import com.homeworkoutcoach.app.ui.components.Logo
import com.homeworkoutcoach.app.ui.components.Muted
import com.homeworkoutcoach.app.ui.components.PrimaryButton
import com.homeworkoutcoach.app.ui.components.Small
import com.homeworkoutcoach.app.ui.theme.AppTheme

/**
 * Welcome and sign-in [vAuth]: the first screen until the user signs in with Google or continues as a guest.
 */
@Composable
fun AuthScreen(appName: String, busy: Boolean, error: String?, onGoogle: () -> Unit, onGuest: () -> Unit) {
    val c = AppTheme.colors
    Box(
        Modifier.fillMaxSize().background(c.bg).safeDrawingPadding().verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 56.dp, bottom = 40.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Logo(72.dp)
                H1(appName)
                Body("Sign in to keep your plan and workout history.", color = c.mut)
            }

            GoogleButton(if (busy) "Signing in…" else "Continue with Google", onClick = { if (!busy) onGoogle() })
            error?.let {
                Text(it, Modifier.padding(top = 10.dp), color = c.bad, style = AppTheme.type.bodyStrong)
            }

            OrDivider()
            PrimaryButton("Try without an account", onGuest, alt = true)
            Muted("New here? Continuing with Google creates your account.", Modifier.padding(top = 18.dp))
        }
    }
}

@Composable
private fun GoogleButton(label: String, onClick: () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.padding(top = 24.dp).fillMaxWidth().heightIn(min = 54.dp).clip(shape)
            .background(c.bg).border(1.dp, c.bd, shape)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
        Image(GoogleG, contentDescription = null, modifier = Modifier.size(20.dp))
        Body(label, strong = true)
    }
}

@Composable
private fun OrDivider() {
    val c = AppTheme.colors
    Row(Modifier.padding(top = 18.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f).height(1.dp).background(c.bd))
        Small("or")
        Box(Modifier.weight(1f).height(1.dp).background(c.bd))
    }
}

/** Google "G" mark, from the prototype's 48x48 SVG. */
private val GoogleG: ImageVector = ImageVector.Builder("google", 20.dp, 20.dp, 48f, 48f).apply {
    fun fill(color: Long, d: String) = addPath(addPathNodes(d), fill = SolidColor(Color(color)))
    fill(0xFFEA4335, "M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z")
    fill(0xFF4285F4, "M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z")
    fill(0xFFFBBC05, "M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z")
    fill(0xFF34A853, "M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z")
}.build()
