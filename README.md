## Zvuk.com downloader

A tool for downloading from zvuk.com.

### Parameters

```
-t, track           Download a track (parameter: id)
-d, discography     Download all released tracks of an artist (parameter: id)
-r  release         Download all tracks from a release - album (paramater: id)
-a, authToken       Authentication Token
```

### Doesn't work?

**PLEASE** just make an issue. It's most likely an easy fix, but I simply don't have the energy to recheck their API every day for micro changes. If you do make an issue, it will likely be resolved very fast.

### Authentication Token

You can find the authentication token of your account by looking at the headers on one of the requests sent when you enter the zvuk.com website.

![img0](https://i.imgur.com/pFb61re.png)
![img1](https://i.imgur.com/Xv5QSgn.png)
