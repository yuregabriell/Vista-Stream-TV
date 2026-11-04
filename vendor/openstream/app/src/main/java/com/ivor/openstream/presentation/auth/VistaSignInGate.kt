package com.ivor.openstream.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * No navigation, catalogue or player is composed until an encrypted
 * Supabase Auth session exists. There is no guest/skip route.
 */
@Composable
fun VistaSignInGate(
    onLogin: () -> Unit,
    onGetVistaId: () -> Unit
) {
    val accent = Color(0xFF8762F5)
    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(Color(0xFF0E1225), Color(0xFF11152E), Color(0xFF211B49))
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 580.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 38.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(68.dp)
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "VistaStream",
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(28.dp))
            Text(
                "A tua conta Vista",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "O login é obrigatório para entrar no VistaStream. " +
                    "Usa a tua conta Vista ID e, depois, escolhe quem está assistindo.",
                color = Color(0xFFD3D3E6),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(30.dp))
            Button(
                onClick = onLogin,
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                modifier = Modifier.fillMaxWidth().height(58.dp)
            ) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text("Entrar com Vista ID", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(22.dp))
            Text(
                "Ainda não tens conta? Instala o Vista ID:",
                color = Color(0xFFC8C8DD),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onGetVistaId,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text("Baixar Vista ID", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "O botão abre a release atual do Vista ID no GitHub.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFACAFC9),
                textAlign = TextAlign.Center
            )
        }
    }
}
