# cloudstream-extensions-arabic
Arabic content provider plugins for the CloudStream Android app.

## About
A collection of content provider plugins for [CloudStream](https://github.com/recloudstream/cloudstream), an open-source Android streaming app. Each provider scrapes and serves Arabic-language media from a different website, allowing users to browse, search, and stream content directly through the app.

## Supported providers
Akwam, Anime4up, AnimeBlkom, Animeiat, ArabSeed, CimaNow, EgyBest, FaselHD, Fushaar, GateAnime, Movizland, MyCima, Shahid4u, FajerShow

## How it works
CloudStream loads provider plugins at runtime directly from this GitHub repository. Each provider implements HTTP scraping and video extraction logic specific to its target website, built using the official CloudStream plugin template.

## Tech stack
Kotlin · Gradle · GitHub Actions · HTML parsing · HTTP client requests · JSON extraction

## Contributing
Pull requests are welcome. Open an issue first if you'd like to request a specific site.
