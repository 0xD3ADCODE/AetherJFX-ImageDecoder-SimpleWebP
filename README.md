[![AetherJFX](https://img.shields.io/badge/Only%20for:-AetherJFX-blue)](https://github.com/0xD3ADCODE/AetherJFX)
![Java](https://img.shields.io/badge/Java-17-b07219)

# AetherJFX (Simple) WebP Image Decoder Plugin

> [!WARNING]  
> This plugin is designed to be used ONLY with [JavaFX](https://github.com/openjdk/jfx) fork [AetherJFX](https://github.com/0xD3ADCODE/AetherJFX). Attempting to use it with standard JavaFX will lead to an exception!

[WebP](https://developers.google.com/speed/webp) image decoding plugin for [AetherJFX](https://github.com/0xD3ADCODE/AetherJFX)

Based on [SimpleWEBP](https://github.com/burningtnt/SimpleWEBP) and integrates into JavaFX's `ImageIO` (`IIO`) without bytecode manipulations

> [!IMPORTANT]  
> This plugin written in pure Java, don't require any native libraries and DOES NOT support Lossless and Animated WebP!

## Dependency

Define custom Gradle ivy repository in `repositories` block:
```groovy
repositories {
    //...your repositories
    def github = ivy {
        url 'https://github.com/'
        patternLayout {
            artifact '/[organisation]/[module]/releases/download/[revision]/[artifact].[ext]'
        }
        metadataSources { artifact() }
    }
    exclusiveContent {
        forRepositories(github)
        filter { includeGroup("0xD3ADCODE") }
    }
}
```

Add dependency into `dependencies` block:
```groovy
dependencies {
    //...your dependencies
    implementation("0xD3ADCODE:AetherJFX-ImageDecoder-SimpleWebP:{version}") {
        artifact {
            name = 'AetherJFX-ImageDecoder-SimpleWebP-{version}'
            type = 'jar'
        }
    }
}
```

Replace `{version}` with latest [Release](https://github.com/0xD3ADCODE/AetherJFX-ImageDecoder-SimpleWebP/releases) tag (eg, `v1.0`)

## Usage
Register plugin as soon as possible (before JavaFX Toolkit start) with just one line of code:
```java
SimpleWEBPLoader.register();
```

After that all WebP images will be decoded using newly installed decoder directly into JavaFX's `Image`

## Credits
[Google](https://developers.google.com) for [WebP](https://developers.google.com/speed/webp) decoder  
[burningtnt](https://github.com/burningtnt/) for [SimpleWEBP](https://github.com/burningtnt/SimpleWEBP) WebP decoder/encoder implementation for Java