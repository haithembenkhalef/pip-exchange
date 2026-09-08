# pip-exchange

A lightweight, price-time priority order matching engine for financial instruments, built in Java on top of Quarkus. The core matching logic is written as a standalone, dependency-free module — Quarkus only wraps it for future service exposure.

## Overview

`pip-exchange` implements the core of a limit order book: an in-memory data structure that holds resting orders and a matching engine that runs incoming orders against it using strict price-time (FIFO) priority. It is designed the way production matching engines are: deterministic, single-threaded per book, and built on fixed-point integer arithmetic rather than floating point.

The project is in active early development. The current codebase covers the foundational order book and matching logic; the REST/API layer, order lifecycle management, and operational concerns (persistence, risk checks, market data) are being built on top of this core.

## Features

### Order book

- Two-sided book per symbol, backed by sorted price levels (bids ordered highest-first, asks ordered lowest-first).
- Each price level maintains strict FIFO ordering of resting orders — first in, first matched.
- O(log n) order lookup, insertion, and removal by order ID, independent of book depth.
- Automatic cleanup of empty price levels.
- Structural integrity checks: the book fails loudly on invariant violations (e.g. out-of-sequence insertion, orphaned index entries) instead of silently drifting into an inconsistent state.

### Matching engine

- Price-time priority matching: incoming orders sweep the opposite side of the book best-price-first, consuming resting orders in strict arrival order within each price level.
- Execution price is always the resting (maker) order's price, giving the incoming (taker) order price improvement when the market has moved in its favor.
- Partial fills preserve queue priority — a partially filled order keeps its place at the head of the line rather than being re-queued.
- Any unfilled remainder of an incoming order rests on the book under Good-Till-Cancel (GTC) semantics.
- Produces an immutable trade record for every match, alongside a result summary (filled quantity, remaining quantity, resting status) for the processed order.

### Order cancellation

- Orders can be removed from the book by ID from anywhere in their price level's queue, not just the head, with the book's internal index and price levels kept consistent.

### Symbol configuration

- Per-symbol specification defining tick size, lot size, minimum/maximum order quantity, and minimum/maximum price.
- Validation of incoming price/quantity pairs against a symbol's rules before they can reach the matching engine.
- Conversion helpers between human-readable decimal values and the engine's internal fixed-point representation, so the matching core never has to reason about decimal places or floating-point rounding.
- Central, thread-safe registry for looking up symbol specifications.

### Determinism and identity

- Monotonic sequence generator used both for order arrival ordering (the tie-breaker for FIFO priority) and for trade IDs, keeping the matching process deterministic and reproducible from a sequence of inputs.

### Test coverage

- Unit tests for the order book, price level, order, matching engine, and symbol specification, covering resting, matching, partial fills, cancellation, and validation behavior.

## What's not built yet

The following are defined in the domain model (e.g. as enum values) but are explicitly rejected or unimplemented in the current matching loop, by design, so the core stays correct and easy to reason about before it grows:

- Immediate-or-Cancel (IOC), Fill-or-Kill (FOK), and Market order types — only Good-Till-Cancel (GTC) is currently handled.
- Order modification ("move") without a full cancel and re-place.
- A public REST API surface for submitting, cancelling, or querying orders.

See [Roadmap](#roadmap) for where these are headed.

## Tech stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Quarkus 3.39 |
| Build | Maven (via Maven Wrapper) |
| Testing | JUnit 5, REST Assured |
| Packaging | JVM jar, über-jar, and GraalVM native image (with Docker build support) |

## Project structure

```
src/main/java/com/exchange/pip/core/
├── orderbook/
│   ├── Order.java            # Immutable order identity + mutable remaining quantity
│   ├── OrderBook.java        # Per-symbol book: sorted price levels + order index
│   ├── OrderType.java        # GTC, IOC, FOK, MARKET
│   ├── Side.java             # BID, ASK
│   ├── PriceLevel.java       # FIFO queue of orders at a single price
│   ├── MatchingEngine.java   # Price-time priority match loop
│   └── MatchResult.java      # Outcome of processing one order
├── symbol/
│   ├── SymbolSpecification.java  # Tick size, lot size, bounds, scale conversion
│   └── SymbolRegistry.java       # Symbol lookup
├── trade/
│   └── Trade.java            # Emitted on every match
└── shared/
    └── IdGenerator.java      # Monotonic sequence generator
```

## Getting started

### Prerequisites

- Java 21 or later
- No local Maven installation required — the project ships with the Maven Wrapper (`mvnw`)

### Run in dev mode

Dev mode enables live coding: changes to source files are picked up without a manual restart.

```shell
./mvnw quarkus:dev
```

The Quarkus Dev UI is available at `http://localhost:8080/q/dev/` while running in this mode.

### Run the tests

```shell
./mvnw test
```

### Package the application

```shell
./mvnw package
```

This produces `quarkus-run.jar` in `target/quarkus-app/`. Dependencies are copied into `target/quarkus-app/lib/` rather than bundled into a single jar.

Run it with:

```shell
java -jar target/quarkus-app/quarkus-run.jar
```

To build a single über-jar instead:

```shell
./mvnw package -Dquarkus.package.jar.type=uber-jar
java -jar target/*-runner.jar
```

### Build a native executable

Requires GraalVM, or Docker if you'd rather build inside a container.

```shell
./mvnw package -Dnative
```

Or, without a local GraalVM installation:

```shell
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

Run the resulting binary with:

```shell
./target/exchange-pip-1.0.0-SNAPSHOT-runner
```

## Roadmap

The core matching loop was deliberately built in stages, correctness-first. Planned next steps, roughly in order:

- **Additional order types** — implement IOC, FOK, and MARKET order handling in the matching engine, reusing the existing sweep logic.
- **Order modification** — support in-place price/quantity amendment without losing more priority than a cancel-and-replace would.
- **Self-trade prevention** — detect and handle a taker matching against resting orders from the same user.
- **REST API layer** — expose order submission, cancellation, and book/query endpoints over HTTP via Quarkus REST.
- **Balance and risk checks** — validate available balance/holds before an order is accepted into the engine.
- **Fee calculation** — compute and attach maker/taker fees to generated trades.
- **Market data publication** — stream book updates and trade prints to subscribers (e.g. via WebSocket or a message bus).
- **Journaling and replay** — persist the input command sequence so engine state can be reconstructed deterministically, and explore a high-performance "direct" (array/radix-tree-backed) order book implementation as an alternative to the current `TreeMap`-based one.

## Contributing

This project is under active development. Issues and pull requests are welcome — please open an issue to discuss significant changes before submitting a PR.

## License

No license has been specified yet for this repository. Until one is added, all rights are reserved by the author.