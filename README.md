# Overview 
A simple APK to quickly apply Mods for the game Last Origin.

Require Android version 4.1 or above. Does not work with Android 13.

This project is experimental, and game patching is not supported by developers. 

**USE AT YOUR OWN RISK!**
# Usage
* Install the APK
* Give Permissions to access files and folders
* (For Android 10 or above) Tap **GRANT PERMISSION**, then **USE THIS FOLDER** in **Android/data** to allow access to Android Storage. 
  * The switches for versions of the Game should be toggleale now.
* Tap **SELECT MOD FOLDER** and select the folder holding all the Mods to be applied. 
  * The Mod Folder location should be shown right below the button.
* Tap **PATCH!**
# Attentions
* Experimental, might not work on some phones. Contact me on Discord (Altter#7252) if you encounter any errors.
* Make sure to let the game download all its assets before patching.
  * Weekly updates might cause some of the files to be replaced. You can use the APK to apply Mods again.
* The folders inside the Mod Folder should have the correct name corresponding to the one to be replaced inside **/files/UnityCache/Shared** folder.
* The asset must be named **__data**. It must also be at most 1 subfolder deep inside the Mod folder.
  * For example, both **Mod Folder/localization/__data** and **Mod Folder/localization/gibberish/__data** will correcty place the mod inside **localization** folder, but not **Mod Folder/localization/gibberish/gibberish 2/__data**
* (For Android 10 or above) There must be only one subfolder inside the modded folder, **__data** must also exist already inside that folder.
* If anything break, delete the modded folders inside **/files/UnityCache/Shared**, then reopen the game to download assets again.