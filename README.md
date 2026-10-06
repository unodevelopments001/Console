<p align="center">
  <img src="https://raw.githubusercontent.com/unodevelopments001/Console/main/docs/banner.svg" alt="Console" width="720">
</p>

**Console** is a phone app for a server you already know. Save a password login, open a shell, move files, and reach SQL Server through that same SSH session.

It is made by **UnoDevelopments**. The current release is **1.1**. It runs on Android 13 and newer.

<p align="center">
  <img alt="Android 13 or newer" src="https://img.shields.io/badge/Android-13%2B-1565C0">
  <img alt="Version 1.1" src="https://img.shields.io/badge/release-1.1-455A64">
</p>

## What you get

The home screen is two doors: **SSH** and **Database**.

SSH keeps a list of servers. Tap one and you get a normal interactive shell, plus a file browser on the same connection. You can save commands and send them again with a tap. Files can be opened as text, uploaded, downloaded, or deleted. Text editing stops at 1 MB. Downloads stop at 64 MB.

Database connections ride that SSH session. Console opens a tunnel and signs in to SQL Server with a SQL login, usually `127.0.0.1` port `1433` on the far side. From there you can list databases, open tables and views, read procedures and functions, edit rows when a table has a primary key, run your own SQL, keep a short query history, and export a grid to CSV.

If the shell or the SQL session drops, you stay on that screen and tap **Reconnect**.

The first time you open the app it asks you to read the terms, pick a look, and decide whether Console should lock behind your fingerprint, face, or device PIN. Themes, info, and the lock switch stay in the three-dot menu after that.

Looks you can pick: Light, Dark, Hacker, Sci-fi, Console, and Custom. Layout and tab style can be changed on their own.

## Install

The signed release sits here:

`apk/Console-1.1.apk`

Copy it to the phone and install it. Later updates have to be signed with the same key. A fresh install, or an uninstall, drops the saved connections on that device.

## Built with

Console is written in **Kotlin 2.2.10**. The screens are Jetpack Compose and Material 3. Connections are stored with Room. The shell and files go through SSHJ. SQL Server goes through jTDS, on a tunnel from that same SSH session.

The project is an **Android Studio** project. This copy is set to the Studio runtime **JBR 25**. The app itself compiles to Java 11, so JDK 17 or newer is enough to build it.

| What | Version |
| --- | --- |
| Language | Kotlin 2.2.10 |
| UI | Jetpack Compose, Material 3 (BOM 2026.02.01) |
| Android | 13 and newer (API 33), target API 37 |
| App version | 1.1, version code 2 |
| Package | `com.unodevelopments.cblsshmngr` |
| Gradle | 9.6.0 |
| Android Gradle Plugin | 9.4.1 |
| Room | 2.8.5, generated with KSP 2.2.10-2.0.2 |
| SSH | SSHJ 0.41.1 |
| SQL Server driver | jTDS 1.3.1 |

## Build it yourself

You need Android SDK 37 and JDK 17 or newer. Android Studio, with this project’s JBR 25 runtime, is the setup used here.

```bat
gradlew.bat :app:assembleRelease
```

The release build is signed when `keystore/keystore.properties` is present. That folder is local. It holds `console-release.jks` (alias `console`) and the password file. Keep both. If that key is lost, phones that already have Console installed cannot take an update.

The debug build is `gradlew.bat :app:assembleDebug`. It uses the debug key, so it will not update a phone that has the release APK.

## Worth knowing before you connect

- Logins are password only.
- The host key is remembered the first time you accept it. If it changes later, Console stops and tells you.
- Passwords are encrypted on the device. The edit screen does not show them back to you.
- SQL Server is reached through SSH. This is not a REST client, and it is not a MySQL client.
- Windows authentication and named instances are a poor fit.
- Views open as read-only grids. Tables without a primary key can be read, not edited in place.
- App lock needs a screen lock on the phone. If the phone has none, Console says so and lets you leave the lock off.

## Terms, in short

You use Console at your own risk.

It can run commands, change files, edit rows, and drop tables, views, procedures, functions, and databases. Those actions can remove data for good.

UnoDevelopments will not be held accountable for data loss caused by misuse of the app. You are responsible for the servers you connect to, the passwords you save, and every command or query you run.

The full wording is on the first launch, and again under **Info → Terms**.
