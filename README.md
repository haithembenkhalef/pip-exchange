# pip-exchange

A lightweight order matching engine for financial instruments, built in Java on Quarkus.

## Overview

`pip-exchange` is the core of a trading exchange: it keeps track of buy and sell orders for a symbol and matches them against each other the way a real exchange does — best price first, and first-come-first-served among orders at the same price. The project is in early, active development. The matching logic itself is solid and tested; the parts that would turn it into a full service (an API, persistence, risk checks) are still to come.

## What it does today

- **Holds a live order book per symbol.** Buy and sell orders rest on the book at their price, waiting to be matched.
- **Matches orders automatically.** When a new order comes in, it's checked against the best-priced orders on the opposite side. If it can trade, it does — trading at the price of the order that was already waiting, so newer orders get the benefit if the market has moved their way.
- **Respects fairness.** Orders at the same price are matched in the order they arrived. A partial fill doesn't send you to the back of the line.
- **Fills what it can, rests the rest.** If an order can't be fully matched, whatever's left over simply waits on the book until it can be.
- **Supports cancellation.** Any resting order can be pulled from the book on request, wherever it sits in the queue.
- **Understands each symbol's rules.** Every tradable symbol has its own price and quantity rules (minimums, maximums, and allowed increments), and orders are checked against them before they're accepted.
- **Keeps everything predictable.** Every order and trade is stamped with a strict, increasing sequence, so the same sequence of inputs always produces the same result — important for testing, auditing, and replaying history later.
- **Is well tested.** The order book and matching behavior — resting, matching, partial fills, cancellations, validation — are all covered by automated tests.

## What's not built yet

- Order types beyond the standard "rest until filled or cancelled" behavior — instant-or-cancel, fill-completely-or-cancel, and market orders are planned but not yet active.
- Editing an existing order's price or quantity in place (currently you'd cancel and re-submit).
- A public API for placing, cancelling, or checking orders from outside the engine.

See [Roadmap](#roadmap) below for what's next.

## Getting started

Requires Java 21. No local Maven install needed — the project ships with the Maven Wrapper.

Run it in dev mode (live reload on changes):

```shell
./mvnw quarkus:dev
```

Run the test suite:

```shell
./mvnw test
```

Build a runnable package:

```shell
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```

## Roadmap

Roughly in order of what's coming next:

- **More order types** — instant-or-cancel, fill-or-kill, and market orders.
- **Order editing** — amend a resting order's price or quantity without a full cancel-and-replace.
- **Self-trade prevention** — stop a user's own orders from matching against each other.
- **A REST API** — place, cancel, and query orders over HTTP.
- **Balance and risk checks** — make sure an order is actually backed by funds before it's accepted.
- **Fees** — calculate and attach trading fees to executed trades.
- **Market data** — publish live book and trade updates to anyone watching.
- **Journaling and replay** — persist every order so the exchange's state can be rebuilt and audited after the fact.

## Contributing

This project is under active development. Issues and pull requests are welcome — please open an issue to discuss significant changes before submitting a PR.

## License

No license has been specified yet for this repository. Until one is added, all rights are reserved by the author.