#!/bin/bash
sed -i '/Spacer(modifier = Modifier.height(80.dp))/i \
            // Admin Utilities Section\n\
            var showAdminPinDialog by remember { mutableStateOf(false) }\n\
            var adminPinInput by remember { mutableStateOf("") }\n\
            var resetStatus by remember { mutableStateOf("") }\n\
\n\
            Spacer(modifier = Modifier.height(16.dp))\n\
            Text(text = "ADMIN UTILITIES", color = Color(0xFFC62828), fontWeight = FontWeight.Black, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))\n\
            \n\
            Card(\n\
                colors = CardDefaults.cardColors(containerColor = Color.White),\n\
                shape = RoundedCornerShape(24.dp),\n\
                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(24.dp))\n\
            ) {\n\
                Column(\n\
                    modifier = Modifier.fillMaxWidth().padding(18.dp),\n\
                    verticalArrangement = Arrangement.spacedBy(10.dp)\n\
                ) {\n\
                    Text(text = "Danger Zone", color = Color(0xFFC62828), fontWeight = FontWeight.Bold, fontSize = 14.sp)\n\
                    Text(text = "Reset the fleet driver ID counter back to DRV-0011 and clear all driver mappings.", color = Color(0xFF64748B), fontSize = 12.sp)\n\
                    Button(\n\
                        onClick = { showAdminPinDialog = true },\n\
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),\n\
                        shape = RoundedCornerShape(12.dp),\n\
                        modifier = Modifier.fillMaxWidth().height(48.dp)\n\
                    ) {\n\
                        Text(text = "RESET DRIVER ID COUNTER", fontWeight = FontWeight.Bold)\n\
                    }\n\
                    if (resetStatus.isNotBlank()) {\n\
                        Text(text = resetStatus, color = if (resetStatus.contains("Success")) Color(0xFF2E7D32) else Color(0xFFC62828), fontSize = 12.sp, fontWeight = FontWeight.Bold)\n\
                    }\n\
                }\n\
            }\n\
\n\
            if (showAdminPinDialog) {\n\
                AlertDialog(\n\
                    onDismissRequest = { showAdminPinDialog = false; adminPinInput = "" },\n\
                    title = { Text("Admin PIN Required") },\n\
                    text = {\n\
                        OutlinedTextField(\n\
                            value = adminPinInput,\n\
                            onValueChange = { adminPinInput = it },\n\
                            label = { Text("Enter PIN") },\n\
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),\n\
                            singleLine = true\n\
                        )\n\
                    },\n\
                    confirmButton = {\n\
                        Button(onClick = {\n\
                            if (adminPinInput == "2481") {\n\
                                resetStatus = "Resetting..."\n\
                                com.example.data.remote.FirebaseSyncManager.resetDriverIdCounter { success ->\n\
                                    resetStatus = if (success) "Successfully reset counter and cleared mappings!" else "Failed to reset counter."\n\
                                }\n\
                                showAdminPinDialog = false\n\
                                adminPinInput = ""\n\
                            } else {\n\
                                resetStatus = "Invalid PIN"\n\
                                showAdminPinDialog = false\n\
                                adminPinInput = ""\n\
                            }\n\
                        }) {\n\
                            Text("CONFIRM")\n\
                        }\n\
                    },\n\
                    dismissButton = {\n\
                        TextButton(onClick = { showAdminPinDialog = false; adminPinInput = "" }) {\n\
                            Text("CANCEL")\n\
                        }\n\
                    }\n\
                )\n\
            }\n\
' app/src/main/java/com/example/ui/screens/SettingsScreen.kt
