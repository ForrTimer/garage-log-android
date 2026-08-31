package com.garagelog.app.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.garagelog.app.data.entity.PhotoEntity
import com.garagelog.app.data.entity.PhotoOwnerType
import com.garagelog.app.ui.GarageLogViewModel
import com.garagelog.app.ui.theme.Surface2
import com.garagelog.app.ui.theme.TextDim
import java.io.File

@Composable
fun PhotoGridSection(
    viewModel: GarageLogViewModel,
    ownerType: PhotoOwnerType,
    ownerId: String,
) {
    val photos by viewModel.observePhotosForOwner(ownerType, ownerId).collectAsState(initial = emptyList())
    var photoPendingDelete by remember { mutableStateOf<PhotoEntity?>(null) }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) viewModel.addPhoto(ownerType, ownerId, uri)
    }

    Text("Photos", style = MaterialTheme.typography.labelMedium, color = TextDim, modifier = Modifier.padding(top = 12.dp))

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(photos, key = { it.id }) { photo ->
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { photoPendingDelete = photo },
            ) {
                AsyncImage(
                    model = File(photo.filePath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(76.dp),
                )
            }
        }
        item {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Surface2)
                    .clickable { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.AddAPhoto, contentDescription = "Add photo", tint = TextDim)
            }
        }
    }

    photoPendingDelete?.let { photo ->
        ConfirmDialog(
            title = "Delete photo?",
            message = "This can't be undone.",
            onConfirm = { viewModel.deletePhoto(photo) },
            onDismiss = { photoPendingDelete = null },
        )
    }
}
