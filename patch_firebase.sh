#!/bin/bash
cat << 'INNER_EOF' >> app/src/main/java/com/example/data/remote/FirebaseSyncManager.kt

    fun resetDriverIdCounter(onComplete: (Boolean) -> Unit) {
        val lastIdRef = database.getReference("metadata").child("lastDriverId")
        lastIdRef.setValue(0L).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // Clear orphan mapping entries
                database.getReference("drivers").removeValue().addOnCompleteListener { mappingTask ->
                    onComplete(mappingTask.isSuccessful)
                }
            } else {
                onComplete(false)
            }
        }
    }
}
INNER_EOF
