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


<img width="1909" height="993" alt="image" src="https://github.com/user-attachments/assets/7f302e70-898a-4fa4-9d50-a4a91db1fea1" />
<img width="1910" height="993" alt="image" src="https://github.com/user-attachments/assets/ccf16287-af44-4494-ac16-de90ad37010b" />
<img width="1905" height="1002" alt="image" src="https://github.com/user-attachments/assets/4d42441d-fc6b-4be3-81b1-c7fa94d69a2f" />
<img width="1915" height="997" alt="image" src="https://github.com/user-attachments/assets/d38fd076-07a2-455d-b509-7b2461f31c2d" />
<img width="375" height="226" alt="image" src="https://github.com/user-attachments/assets/b1f404c8-4bec-4337-889d-68c81f5f8cf5" />



