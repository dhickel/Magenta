# GlobalExceptionHandler Passes Null to Its Own Constructor

**Severity:** HIGH | **Status:** open | **Filed:** 2026-05-25 by Reasonix Code

## Summary
`GlobalExceptionHandler.java:32-35` has a no-arg constructor that passes `null` to the real constructor.

## Affected Code
```java
public GlobalExceptionHandler() {
    this(null);  // passes null to the real constructor
}
```

## Impact
The exception handler may operate without required dependencies, leading to degraded error responses or secondary NullPointerExceptions during error handling.

## Recommended Fix
Remove the no-arg constructor and ensure Spring always uses the parameterized one, or provide proper defaults.

## Mirror
Filed as https://github.com/dhickel/Magenta/issues/11
