# Basalt
[![Build Status](https://travis-ci.org/SamOphis/Basalt.svg?branch=master)](https://travis-ci.org/SamOphis/Basalt)
[![Documentation](https://img.shields.io/badge/docs-here-3C96FF.svg)](https://samophis.github.io/Basalt/-basalt/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

A high-performance, **experimental** and fully-async distributed Audio Node server based on [Undertow](https://github.com/undertow-io/undertow), [Magma](https://github.com/napstr/Magma) and [lavaplayer](https://github.com/sedmelluq/lavaplayer) designed for Discord Bots who want a fast and responsive alternative to [Lavalink](https://github.com/Frederikam/Lavalink).

[Example configuration file here (put in working directory).](https://gist.github.com/SamOphis/19091eb0a1c2dd335a3d09b447be1113)

# Features

* Fully asynchronous and scales automatically based on available hardware.
* Documented fully (even private/internal properties).
* Event-based – you send a request, the server dispatches events in response.
* Statistics which can be sent at custom intervals.
* Extensive use of logging.
* Fault tolerant to a high degree. Almost all errors are handled and sent back as events.
* Entirely WebSocket-based – allows for low-latency, 2-way communication which is exactly what is required here.
* Lightweight and pretty fast resource-wise.
* Just as much support for non-JVM clients as JVM ones.
* Basic authentication (optional password support).
* and more...

# Project Status

Basalt, although tested and determined to be production-ready, is still experimental. Some bugs obviously may not have been noticed, sometimes guides may be a little lacking, etc. I don't have a lot of time to devote to development and so I strongly encourage contributions if you feel like some functionality is missing or buggy etc.

A reference client implementation [can be found here](https://github.com/SamOphis/BasaltClient), although it's a little more incomplete and not production-ready as of Wednesday 15th August 2018. Feel free to open a PR and add a link to your own client implementation in the future.

As of the same date, I'm mainly working on the client as well as guides for both it and the Basalt server to ensure others can also make their own clients or use Basalt properly.

> Note: Basalt is partially tested so some things may slip under the radar. Keep this in mind before deciding if Basalt is the right tool for the job.
