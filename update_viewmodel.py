with open('app/src/main/java/com/example/ui/viewmodel/MetroViewModel.kt', 'r') as f:
    content = f.read()

content = content.replace(
    '''                com.example.service.MusicNotificationService.updateNotification(
                    application.applicationContext,
                    track,
                    isPlaying
                )''',
    '''                com.example.service.MusicNotificationService.updateNotification(
                    application.applicationContext,
                    track,
                    isPlaying,
                    _playbackPosition.value
                )'''
)

with open('app/src/main/java/com/example/ui/viewmodel/MetroViewModel.kt', 'w') as f:
    f.write(content)
