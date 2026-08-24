(ns binance-clj.spot
  "Unified public and signed Spot REST facade for connector consumers."
  (:require [binance-clj.filters :as filters]
            [binance-clj.spot.account :as account-api]
            [binance-clj.spot.general :as general]
            [binance-clj.spot.market :as market]
            [binance-clj.spot.streams :as streams-api]
            [binance-clj.spot.trade :as trade-api]
            [binance-clj.spot.user-stream :as user-stream-api]))

(def ping
  "Tests REST connectivity."
  general/ping)

(def server-time
  "Returns Binance server time."
  general/server-time)

(def exchange-info
  "Returns exchange and symbol rules."
  general/exchange-info)

(def ticker-price
  "Returns latest symbol price data."
  market/ticker-price)

(def ticker-24h
  "Returns 24-hour ticker statistics."
  market/ticker-24h)

(def book-ticker
  "Returns best bid/ask data."
  market/book-ticker)

(def depth
  "Returns an order-book snapshot."
  market/depth)

(def account
  "Returns signed account information."
  account-api/account)

(def my-trades
  "Returns signed account trades."
  account-api/my-trades)

(def query-order
  "Returns a signed order query."
  account-api/query-order)

(def open-orders
  "Returns signed open orders."
  account-api/open-orders)

(def validate-order
  "Runs pure local Binance symbol-filter validation without network access."
  filters/validate-order)

(def test-order
  "Validates an order locally and through Binance without execution."
  trade-api/test-order)

(def new-order
  "Submits a locally validated MARKET/LIMIT/STOP_LOSS order."
  trade-api/new-order)

(def submit-order!
  "Submits once and safely reconciles an unknown new-order execution."
  trade-api/submit-order!)

(def cancel-order
  "Cancels an active order."
  trade-api/cancel-order)

(def create-market-stream
  "Creates a shared disconnected Spot market stream manager."
  streams-api/create-stream)

(def create-user-stream
  "Creates a shared disconnected signed Spot User Data Stream manager."
  user-stream-api/create-stream)
