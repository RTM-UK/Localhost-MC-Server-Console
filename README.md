# Localhost-MC-Server-Console
I don’t want to pay to host a server to test plugins, so I made an executable that is essentially what you would get if you bought a server hosting service.

I had to read up on windows file management to allow users to drag and drop files instead of having to open the files in File Explorer. (It was not fun 😂)

## Features

- First launch asks for the Minecraft server `.jar`.
- Starts the server with `java -jar <server.jar> nogui`.
- Shows live server output.
- Sends console commands to the running server.
- Stops the server with the `stop` command.
- Files tab shows the server folder and has quick buttons for `plugins` and `logs`.
- Files tab can import, drag/drop, delete, create folders, and edit small text/config files.
- Players tab can list saved players from `world/playerdata`, preview inventory slots, add/remove items with dialogs, edit item ids/counts/slots, and save with a `.bak` backup.
- Remembers the selected server jar in `%APPDATA%\MC Local Server Console`.
^ Player Invetory Additions & Reductions currently do not work and are a WIP
