import re

with open('app/src/main/java/com/example/data/player/MetroPlayerEngine.kt', 'r') as f:
    content = f.read()

old_block = '''                    if (uriString.startsWith("content://")) {
                        setDataSource(context, Uri.parse(uriString))
                        prepare()
                    } else {
                        val file = java.io.File(track.path)
                        if (track.path.startsWith("/") && file.exists() && file.isFile) {
                            java.io.FileInputStream(file).use { fis ->
                                setDataSource(fis.fd)
                                prepare()
                            }
                        } else {
                            setDataSource(context, Uri.parse(track.path))
                            prepare()
                        }
                    }
                    isPrepared = true'''

new_block = '''                    if (uriString.startsWith("content://")) {
                        setDataSource(context, Uri.parse(uriString))
                    } else {
                        val file = java.io.File(track.path)
                        if (track.path.startsWith("/") && file.exists() && file.isFile) {
                            val fis = java.io.FileInputStream(file)
                            setDataSource(fis.fd)
                            fis.close()
                        } else {
                            setDataSource(context, Uri.parse(track.path))
                        }
                    }
                    prepare()
                    isPrepared = true'''

if old_block in content:
    content = content.replace(old_block, new_block)
    with open('app/src/main/java/com/example/data/player/MetroPlayerEngine.kt', 'w') as f:
        f.write(content)
    print("Reverted engine changes")
else:
    print("Old block not found")

