package org.top500.fetcher;

import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.Point;

import java.util.Set;

public class WaitingConditions {

    private WaitingConditions() {
        // utility class
    }

    private static abstract class ElementTextComperator implements ExpectedCondition<String> {
        private String lastText = "";
        private WebElement element;
        private String expectedValue;
        ElementTextComperator(WebElement element, String expectedValue) {
            this.element = element;
            this.expectedValue = expectedValue;
        }

        public String apply(WebDriver ignored) {
            lastText = element.getText();
            if (compareText(expectedValue, lastText)) {
                return lastText;
            }

            return null;
        }

        abstract boolean compareText(String expectedValue, String actualValue);

        @Override
        public String toString() {
            return "Element text mismatch: expected: " + expectedValue + " but was: '" + lastText + "'";
        }
    }

    public static ExpectedCondition<String> elementTextToEqual(
            final WebElement element, final String value) {
        return new ElementTextComperator(element, value) {

            @Override
            boolean compareText(String expectedValue, String actualValue) {
                return expectedValue.equals(actualValue);
            }
        };
    }

    public static ExpectedCondition<String> elementTextToContain(
            final WebElement element, final String value) {
        return new ElementTextComperator(element, value) {

            @Override
            boolean compareText(String expectedValue, String actualValue) {
                return actualValue.contains(expectedValue);
            }
        };
    }

    public static ExpectedCondition<String> elementTextToEqual(final By locator, final String value) {
        return new ExpectedCondition<String>() {

            @Override
            public String apply(WebDriver driver) {
                String text = driver.findElement(locator).getText();
                if (value.equals(text)) {
                    return text;
                }

                return null;
            }


            @Override
            public String toString() {
                return "element text did not equal: " + value;
            }
        };
    }

    public static ExpectedCondition<String> elementValueToEqual(
            final WebElement element, final String expectedValue) {
        return new ExpectedCondition<String>() {

            private String lastValue = "";

            @Override
            public String apply(WebDriver ignored) {
                lastValue = element.getAttribute("value");
                if (expectedValue.equals(lastValue)) {
                    return lastValue;
                }
                return null;
            }

            @Override
            public String toString() {
                return "element value to equal: " + expectedValue + " was: " + lastValue;
            }
        };
    }

    public static ExpectedCondition<String> pageSourceToContain(final String expectedText) {
        return new ExpectedCondition<String>() {
            @Override
            public String apply(WebDriver driver) {
                String source = driver.getPageSource();

                if (source.contains(expectedText)) {
                    return source;
                }
                return null;
            }

            @Override
            public String toString() {
                return "Page source to contain: " + expectedText;
            }
        };
    }

    public static ExpectedCondition<Point> elementLocationToBe(
            final WebElement element, final Point expectedLocation) {
        return new ExpectedCondition<Point>() {
            private Point currentLocation = new Point(0, 0);

            public Point apply(WebDriver ignored) {
                currentLocation = element.getLocation();
                if (currentLocation.equals(expectedLocation)) {
                    return expectedLocation;
                }

                return null;
            }

            @Override
            public String toString() {
                return "location to be: " + expectedLocation + " is: " + currentLocation;
            }
        };
    }

    public static ExpectedCondition<Set<String>> windowHandleCountToBe(final int count) {
        return new ExpectedCondition<Set<String>>() {
            public Set<String> apply(WebDriver driver) {
                Set<String> handles = driver.getWindowHandles();

                if (handles.size() == count) {
                    return handles;
                }
                return null;
            }
        };
    }

    public static ExpectedCondition<Set<String>> windowHandleCountToBeGreaterThan(final int count) {

        return new ExpectedCondition<Set<String>>() {
            @Override
            public Set<String> apply(WebDriver driver) {
                Set<String> handles = driver.getWindowHandles();

                if (handles.size() > count) {
                    return handles;
                }
                return null;
            }
        };
    }

    public static ExpectedCondition<String> newWindowIsOpened(final Set<String> originalHandles) {
        return new ExpectedCondition<String>() {

            @Override
            public String apply(WebDriver driver) {
                Set<String> currentWindowHandles = driver.getWindowHandles();
                if (currentWindowHandles.size() > originalHandles.size()) {
                    currentWindowHandles.removeAll(originalHandles);
                    return currentWindowHandles.iterator().next();
                } else {
                    return null;
                }
            }
        };

    }

    public static ExpectedCondition<WebDriver> windowToBeSwitchedToWithName(final String windowName) {
        return new ExpectedCondition<WebDriver>() {

            @Override
            public WebDriver apply(WebDriver driver) {
                return driver.switchTo().window(windowName);
            }

            @Override
            public String toString() {
                return String.format("window with name %s to exist", windowName);
            }
        };
    }

    public static ExpectedCondition<Boolean> onlywait() {
        return new ExpectedCondition<Boolean>() {
            @Override
            public Boolean apply(WebDriver driver) {
                return false;
            }
            @Override
            public String toString() {
                return String.format("onlywait");
            }
        };
    }

    public static ExpectedCondition<String> elementTextChanged(final By locator, final String value) {
        return new ExpectedCondition<String>() {

            @Override
            public String apply(WebDriver driver) {
                String text = driver.findElement(locator).getText();
                if (!value.equals(text)) {
                    return text;
                }
                return null;
            }

            @Override
            public String toString() {
                return "element text changed from: " + value;
            }
        };
    }

    public static ExpectedCondition<String> elementValueChanged(final By locator, final String value) {
        return new ExpectedCondition<String>() {

            @Override
            public String apply(WebDriver driver) {
                String newvalue = driver.findElement(locator).getAttribute("value");
                if (!value.equals(newvalue)) {
                    return newvalue;
                }
                return null;
            }

            @Override
            public String toString() {
                return "element value changed from: " + value;
            }
        };
    }
}
