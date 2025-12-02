package com.rama.ramablemanager.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rama.blecore.model.BleDevice

@Composable
fun BleDeviceCard(
    bleDevice: BleDevice,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            Text(
                text = bleDevice.name,
                modifier = Modifier.padding(start = 10.dp, top = 10.dp)
            )
            Spacer(modifier = Modifier.height(5.dp))
            Row {
                Text(
                    text = bleDevice.address,
                    modifier = Modifier.padding(start = 10.dp, top = 10.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "RSSI ${bleDevice.rssi}",
                    modifier = Modifier.padding(start = 10.dp, top = 10.dp)
                )
            }
        }
    }
}

