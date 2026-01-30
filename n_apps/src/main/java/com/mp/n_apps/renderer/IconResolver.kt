package com.mp.n_apps.renderer

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector

object IconResolver {

    private val iconMap: Map<String, ImageVector> = mapOf(
        "add" to Icons.Default.Add,
        "arrow_back" to Icons.AutoMirrored.Filled.ArrowBack,
        "arrow_forward" to Icons.AutoMirrored.Filled.ArrowForward,
        "arrow_downward" to Icons.Default.ArrowDownward,
        "arrow_upward" to Icons.Default.ArrowUpward,
        "bookmark" to Icons.Default.Bookmark,
        "build" to Icons.Default.Build,
        "call" to Icons.Default.Call,
        "camera" to Icons.Default.Camera,
        "check" to Icons.Default.Check,
        "check_circle" to Icons.Default.CheckCircle,
        "clear" to Icons.Default.Clear,
        "close" to Icons.Default.Close,
        "code" to Icons.Default.Code,
        "content_copy" to Icons.Default.ContentCopy,
        "copy" to Icons.Default.ContentCopy,
        "create" to Icons.Default.Create,
        "date_range" to Icons.Default.DateRange,
        "delete" to Icons.Default.Delete,
        "done" to Icons.Default.Done,
        "download" to Icons.Default.Download,
        "edit" to Icons.Default.Edit,
        "email" to Icons.Default.Email,
        "error" to Icons.Default.Error,
        "favorite" to Icons.Default.Favorite,
        "favorite_border" to Icons.Default.FavoriteBorder,
        "filter_list" to Icons.Default.FilterList,
        "folder" to Icons.Default.Folder,
        "folder_open" to Icons.Default.FolderOpen,
        "home" to Icons.Default.Home,
        "image" to Icons.Default.Image,
        "info" to Icons.Default.Info,
        "keyboard_arrow_down" to Icons.Default.KeyboardArrowDown,
        "keyboard_arrow_up" to Icons.Default.KeyboardArrowUp,
        "link" to Icons.Default.Link,
        "list" to Icons.AutoMirrored.Filled.List,
        "location_on" to Icons.Default.LocationOn,
        "lock" to Icons.Default.Lock,
        "menu" to Icons.Default.Menu,
        "more_vert" to Icons.Default.MoreVert,
        "notifications" to Icons.Default.Notifications,
        "pause" to Icons.Default.Pause,
        "person" to Icons.Default.Person,
        "phone" to Icons.Default.Phone,
        "place" to Icons.Default.Place,
        "play_arrow" to Icons.Default.PlayArrow,
        "refresh" to Icons.Default.Refresh,
        "remove" to Icons.Default.Remove,
        "search" to Icons.Default.Search,
        "send" to Icons.AutoMirrored.Filled.Send,
        "settings" to Icons.Default.Settings,
        "share" to Icons.Default.Share,
        "shopping_cart" to Icons.Default.ShoppingCart,
        "star" to Icons.Default.Star,
        "stop" to Icons.Default.Stop,
        "thumb_up" to Icons.Default.ThumbUp,
        "upload" to Icons.Default.Upload,
        "visibility" to Icons.Default.Visibility,
        "visibility_off" to Icons.Default.VisibilityOff,
        "warning" to Icons.Default.Warning,
    )

    private val fallback: ImageVector = Icons.Default.Info

    fun resolveIcon(name: String?): ImageVector {
        if (name == null) return fallback
        return iconMap[name.lowercase()] ?: fallback
    }
}
