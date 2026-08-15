FROM golang:1.23-alpine AS build
WORKDIR /app
COPY . .
RUN go mod download && CGO_ENABLED=0 go build -o /server ./cmd/server

FROM alpine:3.20
WORKDIR /app
COPY --from=build /server /server
COPY migrations ./migrations
EXPOSE 8080
CMD ["/server"]
