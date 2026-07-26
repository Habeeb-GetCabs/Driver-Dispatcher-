package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun AdminAuthPinModal(
    onDismiss: () -> Unit,
    onPinSuccess: () -> Unit,
    onVerifyPin: (String) -> Boolean
) {
    var pinText by remember { mutableStateOf("") }
    var isErrorVisible by remember { mutableStateOf(false) }
    var isPasswordVisible by remember { mutableStateOf(false) }

    val redBrand = Color(0xFFC62828)

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color(0xFF1E1E1E),
        unfocusedTextColor = Color(0xFF1E1E1E),
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White,
        focusedBorderColor = redBrand,
        unfocusedBorderColor = Color(0xFFBDBDBD),
        focusedLabelColor = redBrand,
        unfocusedLabelColor = Color(0xFF616161),
        focusedLeadingIconColor = redBrand,
        unfocusedLeadingIconColor = Color(0xFF616161)
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(Color(0xFFFFEBEE), shape = RoundedCornerShape(30.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AdminPanelSettings,
                        contentDescription = "Admin Lock",
                        tint = redBrand,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "DISPATCHER ADMIN LOGIN",
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                    color = Color(0xFF1E1E1E),
                    letterSpacing = 1.sp
                )

                Text(
                    text = "Enter Admin Security Password / PIN to access Fleet Dispatcher Control Panel.",
                    fontSize = 12.sp,
                    color = Color(0xFF616161),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = pinText,
                    onValueChange = {
                        pinText = it
                        isErrorVisible = false
                    },
                    label = { Text("Admin PIN (Default: 1234)") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle password",
                                tint = Color.Gray
                            )
                        }
                    },
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    colors = textFieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_pin_input")
                )

                if (isErrorVisible) {
                    Text(
                        text = "❌ Incorrect Admin Password! Default is 1234",
                        color = redBrand,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("CANCEL", color = Color.Gray, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (onVerifyPin(pinText)) {
                                onPinSuccess()
                            } else {
                                isErrorVisible = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = redBrand),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(48.dp)
                            .testTag("login_admin_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("UNLOCK", color = Color.White, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}
