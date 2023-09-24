FROM golang:1.21-alpine as builder

WORKDIR /app

COPY go.mod .
COPY go.sum .

RUN go mod download

COPY . .

RUN go build -o app .

FROM alpine:3.18.3

WORKDIR /app

COPY --from=builder /app/app .
COPY --from=builder /app/templates ./templates

ENV ENVIRONMENT=production

CMD ["./app"]
