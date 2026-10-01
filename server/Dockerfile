FROM golang:1.22 AS build
WORKDIR /src
COPY go.mod ./
COPY . .
RUN CGO_ENABLED=0 go build -o /out/locket ./cmd/locket

FROM gcr.io/distroless/static-debian12
COPY --from=build /out/locket /locket
VOLUME ["/data"]
EXPOSE 8080
ENTRYPOINT ["/locket"]

