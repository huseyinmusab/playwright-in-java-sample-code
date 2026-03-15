package com.serenitydojo.playwright;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.microsoft.playwright.*;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@Execution(ExecutionMode.SAME_THREAD)
public class PlaywrightRestAPITest {

    protected static Playwright playwright;
    protected static Browser browser;
    protected static BrowserContext browserContext;

    Page page;

    @BeforeAll
    static void setUpBrowser() {
        playwright = Playwright.create();
        playwright.selectors().setTestIdAttribute("data-test");
        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(false)
                        .setArgs(Arrays.asList("--no-sandbox", "--disable-extensions", "--disable-gpu"))
        );
    }

    @BeforeEach
    void setUp() {
        browserContext = browser.newContext();
        page = browserContext.newPage();

        page.navigate("https://practicesoftwaretesting.com");
        page.getByPlaceholder("Search").waitFor();

    }

    @AfterEach
    void closeContext() {
        browserContext.close();
    }

    @AfterAll
    static void tearDown() {
        browser.close();
        playwright.close();
    }

    @DisplayName("Playwright allows us to mock out API responses")
    @Nested
    class MockingAPIResponses {

        @Test
        @DisplayName("When a search returns a single product")
        void whenASingleItemIsFound() {
            page.route("**/products/search?q=pliers",
                    route -> route.fulfill(new Route.FulfillOptions()
                            .setBody(MockSearchResponses.RESPONSE_WITH_A_SINGLE_ENTRY)
                            .setStatus(200))
            );

            var searchBox = page.getByPlaceholder("Search");
            searchBox.fill("pliers");
            searchBox.press("Enter");

            assertThat(page.getByTestId("product-name")).hasCount(1);
            assertThat(page.getByTestId("product-name")
                    .filter(new Locator.FilterOptions().setHasText("Super Pliers")))
                    .isVisible();
        }

        @Test
        @DisplayName("When a search returns no products")
        void whenNoItemsAreFound() {
            page.route("**/products/search?q=pliers",
                    route -> route.fulfill(new Route.FulfillOptions()
                            .setBody(MockSearchResponses.RESPONSE_WITH_NO_ENTRIES)
                            .setStatus(200))
            );
            var searchBox = page.getByPlaceholder("Search");
            searchBox.fill("pliers");
            searchBox.press("Enter");

            assertThat(page.getByTestId("product-name")).isHidden();
            assertThat(page.getByTestId("search_completed")).hasText("There are no products found.");
        }
    }

    @Nested
    class MakingAPICalls {

        record Product(String name, Double price) {}

        private static APIRequestContext requestContext;

        @BeforeAll
        public static void setupRequestContext() {
            requestContext = playwright.request().newContext(
                    new APIRequest.NewContextOptions()
                            .setBaseURL("https://api.practicesoftwaretesting.com")
                            .setExtraHTTPHeaders(new HashMap<>() {{
                                put("Accept", "application/json");
                            }})
            );
        }

        @DisplayName("Check presence of known products")
        @ParameterizedTest(name = "Checking product {0}")
        @MethodSource("products")
        void checkKnownProduct(Product product) {
            page.fill("[placeholder='Search']", product.name);
            page.click("button:has-text('Search')");

            // Check that the product appears with the correct name and price

            Locator productCard = page.locator(".card")
                    .filter(
                        new Locator.FilterOptions()
                                .setHasText(product.name)
                                .setHasText(Double.toString(product.price))
                    );
            assertThat(productCard).isVisible();
        }

        static Stream<Product> products() {
            APIResponse response = requestContext.get("/products?page=2");
            Assertions.assertThat(response.status()).isEqualTo(200);

            JsonObject jsonObject = new Gson().fromJson(response.text(), JsonObject.class);
            JsonArray data = jsonObject.getAsJsonArray("data");

            return data.asList().stream()// Listeyi bir akışa çevir
                    .map(jsonElement -> {// Her bir JSON elemanı için şunları yap:
                        JsonObject productJson = jsonElement.getAsJsonObject();
                        return new Product(
                                productJson.get("name").getAsString(),// "name" alanını metin olarak al
                                productJson.get("price").getAsDouble()// "price" alanını sayı olarak al

                        );
                    })
                    .peek(x -> System.out.println("İşlenen Ürün: " + x.name() + " - " + x.price()));
            // ^ Burada her bir Product oluştuğunda konsola yazar.


        }
//static List<Product> products() {
//    // 1. API'den veriyi al
//    APIResponse response = requestContext.get("/products?page=2");
//
//    // 2. JSON'a çevir
//    JsonObject jsonObject = new Gson().fromJson(response.text(), JsonObject.class);
//    JsonArray dataArray = jsonObject.getAsJsonArray("data");
//
//    //bos list
//    List<Product> urunListesi = new ArrayList<>();
//
//    System.out.println("--- API'den Gelen Ürünler Listeleniyor ---");
//
//    // 3. Her bir ürünü tek tek dön
//    for (JsonElement eleman : dataArray) {
//        JsonObject urunJson = eleman.getAsJsonObject();
//
//        // Verileri ayıkla
//        String isim = urunJson.get("name").getAsString();
//        Double fiyat = urunJson.get("price").getAsDouble();
//
//        // KONSOLA YAZDIRMA: Burası istediğin kısım
//        System.out.println("Ürün Adı: " + isim + " | Fiyatı: " +  );
//
//        // Listeye ekle (Testin çalışabilmesi için bu şart)
//        urunListesi.add(new Product(isim, fiyat));
//    }
//
//    System.out.println("--- Liste Başarıyla Hazırlandı (Toplam: " + urunListesi.size() + " ürün) ---");
//
//    return urunListesi;
//}

    }
}
