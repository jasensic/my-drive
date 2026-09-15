# syntax=docker/dockerfile:1
FROM rust:1.94-bookworm AS builder
WORKDIR /app
COPY Cargo.toml Cargo.lock* ./
COPY crates ./crates
COPY migrations ./migrations
RUN cargo build --release -p mydrive-api

FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates && rm -rf /var/lib/apt/lists/*
COPY --from=builder /app/target/release/mydrive-api /usr/local/bin/mydrive-api
ENV API_HOST=0.0.0.0
EXPOSE 8080
CMD ["mydrive-api"]
