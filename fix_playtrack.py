with open('app/src/main/java/com/example/ui/viewmodel/MetroViewModel.kt', 'r') as f:
    content = f.read()

import re

new_content = re.sub(
    r'    fun playTrack\(track: Track, customQueue: List<Track> = emptyList\(\)\) \{\s*_currentTrack.value = track\s*// Define Queue\s*if \(customQueue.isNotEmpty\(\)\) \{\s*playbackQueue = customQueue\s*\} else \{\s*playbackQueue = allAvailableTracks.value\s*\}\s*_playbackQueueState.value = playbackQueue\s*currentQueueIndex = playbackQueue.indexOfFirst \{ it.id == track.id \}\s*playerEngine.play\(track\)\s*_isPlayingState.value = true\s*_playbackDuration.value = track.durationMs\s*_playbackPosition.value = 0L',
    '''    fun playTrack(track: Track, customQueue: List<Track> = emptyList()) {
        _playbackPosition.value = 0L
        _playbackDuration.value = track.durationMs
        _isPlayingState.value = true
        _currentTrack.value = track
        
        // Define Queue
        if (customQueue.isNotEmpty()) {
            playbackQueue = customQueue
        } else {
            playbackQueue = allAvailableTracks.value
        }
        _playbackQueueState.value = playbackQueue
        currentQueueIndex = playbackQueue.indexOfFirst { it.id == track.id }
        playerEngine.play(track)''',
    content
)

if new_content == content:
    print("No change")
else:
    with open('app/src/main/java/com/example/ui/viewmodel/MetroViewModel.kt', 'w') as f:
        f.write(new_content)
