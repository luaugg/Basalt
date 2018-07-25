# Module Basalt

The main Basalt Module itself, which contains everything inside the project.

Almost everything in each class has the least visibility possible, so don't expect many things to be exposed. You can
find the documentation for each package except the `messages` package here, too.

[Check out Basalt on GitHub](https://github.com/SamOphis/Basalt), if you haven't already!

# Package basalt

Package containing the Main Class, which is essentially used simply to start the application up and to add a shutdown hook
ensuring Basalt properly shuts down.

# Package basalt.player

Package containing everything related to Audio Players or the loading of audio.

# Package basalt.server

Package containing everything related to the Basalt Server, such as the listener that listens for incoming events
as well as the `BasaltServer` class itself.

# Package basalt.util

Package containing a single `AudioTrackUtil` class, used entirely to encode/decode tracks.