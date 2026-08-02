with open('app/src/main/java/com/example/ui/viewmodel/MetroViewModel.kt', 'r') as f:
    content = f.read()

content = content.replace(
    '''    fun seekTo(positionMs: Long) {
        playerEngine.seekTo(positionMs)
        _playbackPosition.value = positionMs
        _currentTrack.value?.let { track ->
            saveLastPlayedTrack(track.id, positionMs)
        }
    }''',
    '''    fun seekTo(positionMs: Long) {
        playerEngine.seekTo(positionMs)
        _playbackPosition.value = positionMs
        _currentTrack.value?.let { track ->
            saveLastPlayedTrack(track.id, positionMs)
            com.example.service.MusicNotificationService.updateNotification(
                getApplication(),
                track,
                _isPlaying.value,
                positionMs
            )
        }
    }'''
)

with open('app/src/main/java/com/example/ui/viewmodel/MetroViewModel.kt', 'w') as f:
    f.write(content)
