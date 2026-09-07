package com.nakitera.brokerage.config;

public final class OpenApiExamples {

    public static final String BUY_ORDER = """
            {
              "customerId": "customer-1",
              "assetName": "THYAO",
              "orderSide": "BUY",
              "size": 10,
              "price": 250.50
            }
            """;

    public static final String SELL_ORDER = """
            {
              "customerId": "customer-1",
              "assetName": "THYAO",
              "orderSide": "SELL",
              "size": 5,
              "price": 248.75
            }
            """;

    public static final String MATCH_ORDERS = """
            {
              "orderIds": [1, 2]
            }
            """;

    public static final String ORDER_CREATED = """
            {
              "id": 1,
              "customerId": "customer-1",
              "assetName": "THYAO",
              "orderSide": "BUY",
              "size": 10,
              "price": 250.50,
              "status": "PENDING",
              "createDate": "2026-05-05T12:00:00Z"
            }
            """;

    public static final String ORDER_CANCELED = """
            {
              "id": 1,
              "customerId": "customer-1",
              "assetName": "THYAO",
              "orderSide": "BUY",
              "size": 10,
              "price": 250.50,
              "status": "CANCELED",
              "createDate": "2026-05-05T12:00:00Z"
            }
            """;

    public static final String ORDER_LIST = """
            [
              {
                "id": 2,
                "customerId": "customer-1",
                "assetName": "ASELS",
                "orderSide": "SELL",
                "size": 3,
                "price": 82.10,
                "status": "PENDING",
                "createDate": "2026-05-05T13:00:00Z"
              },
              {
                "id": 1,
                "customerId": "customer-1",
                "assetName": "THYAO",
                "orderSide": "BUY",
                "size": 10,
                "price": 250.50,
                "status": "PENDING",
                "createDate": "2026-05-05T12:00:00Z"
              }
            ]
            """;

    public static final String ASSET_LIST = """
            [
              {
                "id": 3,
                "customerId": "customer-1",
                "assetName": "ASELS",
                "size": 50.000000,
                "usableSize": 50.000000
              },
              {
                "id": 2,
                "customerId": "customer-1",
                "assetName": "THYAO",
                "size": 100.000000,
                "usableSize": 100.000000
              },
              {
                "id": 1,
                "customerId": "customer-1",
                "assetName": "TRY",
                "size": 100000.000000,
                "usableSize": 100000.000000
              }
            ]
            """;

    public static final String MATCHED_ORDERS = """
            [
              {
                "id": 1,
                "customerId": "customer-1",
                "assetName": "THYAO",
                "orderSide": "BUY",
                "size": 10,
                "price": 250.50,
                "status": "MATCHED",
                "createDate": "2026-05-05T12:00:00Z"
              }
            ]
            """;

    public static final String INSUFFICIENT_BALANCE = """
            {
              "timestamp": "2026-05-05T12:00:00Z",
              "status": 409,
              "code": "INSUFFICIENT_USABLE_BALANCE",
              "message": "Customer does not have sufficient usable TRY balance",
              "path": "/api/v1/orders"
            }
            """;

    public static final String LEDGER_LIST = """
            [
              {
                "id": 1,
                "customerId": "customer-1",
                "assetName": "TRY",
                "sizeDelta": 0.000000,
                "usableDelta": -2505.000000,
                "orderId": 1,
                "reason": "RESERVE",
                "createdAt": "2026-05-05T12:00:00Z"
              },
              {
                "id": 2,
                "customerId": "customer-1",
                "assetName": "TRY",
                "sizeDelta": -2505.000000,
                "usableDelta": 0.000000,
                "orderId": 1,
                "reason": "MATCH_DEBIT",
                "createdAt": "2026-05-05T12:01:00Z"
              },
              {
                "id": 3,
                "customerId": "customer-1",
                "assetName": "THYAO",
                "sizeDelta": 10.000000,
                "usableDelta": 10.000000,
                "orderId": 1,
                "reason": "MATCH_CREDIT",
                "createdAt": "2026-05-05T12:01:00Z"
              }
            ]
            """;

    private OpenApiExamples() {
    }
}
