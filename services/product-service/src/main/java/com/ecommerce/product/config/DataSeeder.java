package com.ecommerce.product.config;

import com.ecommerce.product.document.ProductDocument;
import com.ecommerce.product.model.Product;
import com.ecommerce.product.repository.jpa.ProductRepository;
import com.ecommerce.product.repository.search.ProductSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Demo data seeder. On startup, if the catalog is empty, inserts a curated set of products into
 * Postgres (source of truth) and mirrors them into Elasticsearch (search index) — the same two-store
 * write {@code ProductService.createProduct} performs per product. Idempotent: skips when products
 * already exist. Toggle with {@code seed.products.enabled} (default true). The ES write is best-effort
 * so a slow/late Elasticsearch never blocks Postgres seeding — use {@code POST /products/reindex} to
 * (re)build the index afterwards.
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final ProductRepository productRepository;
    private final ProductSearchRepository searchRepository;

    @Override
    public void run(String... args) {
        long existing = productRepository.count();
        if (existing > 0) {
            log.info("[seed] catalog already has {} products — skipping seed", existing);
            return;
        }

        Random rnd = new Random(42); // fixed seed -> reproducible demo data
        List<Product> products = new ArrayList<>(CATALOG.length);
        for (String[] row : CATALOG) {
            products.add(Product.builder()
                .id(UUID.randomUUID())
                .name(row[0])
                .brand(row[1])
                .category(row[2])
                .price(new BigDecimal(row[3]))
                .description(row[4])
                .stockQuantity(15 + rnd.nextInt(185))                                  // 15..199
                .averageRating(BigDecimal.valueOf(3.6 + rnd.nextDouble() * 1.4)         // 3.6..5.0
                    .setScale(1, RoundingMode.HALF_UP).floatValue())
                .build());
        }

        productRepository.saveAll(products);
        log.info("[seed] inserted {} products into Postgres", products.size());

        try {
            searchRepository.saveAll(products.stream().map(ProductDocument::from).toList());
            log.info("[seed] indexed {} products into Elasticsearch", products.size());
        } catch (Exception e) {
            log.warn("[seed] Elasticsearch indexing failed ({}). Postgres seed is intact; "
                + "run POST /products/reindex once ES is up.", e.toString());
        }
    }

    // {name, brand, category, price, description}
    private static final String[][] CATALOG = {
        // --- Electronics ---
        {"Galaxy S24 Ultra Smartphone", "Samsung", "Electronics", "109999", "6.8-inch Dynamic AMOLED, 200MP camera, Snapdragon 8 Gen 3, 5000mAh battery."},
        {"iPhone 15 Pro", "Apple", "Electronics", "134900", "Titanium frame, A17 Pro chip, 48MP main camera, USB-C, Action button."},
        {"Pixel 8a", "Google", "Electronics", "52999", "Tensor G3, clean Android, 7 years of updates, great computational photography."},
        {"4K Streaming Stick", "Amazon", "Electronics", "4999", "Wi-Fi 6 streaming with Dolby Vision and Atmos; voice remote included."},
        {"Mirrorless Camera A6400", "Sony", "Electronics", "78990", "24MP APS-C sensor, real-time eye autofocus, 4K video, tilting touchscreen."},
        {"Power Bank 20000mAh", "Anker", "Electronics", "2799", "65W USB-C PD fast charge, charges laptops and phones, dual-port output."},

        // --- Computers ---
        {"MacBook Air M3 13-inch", "Apple", "Computers", "114900", "M3 chip, 18-hour battery, fanless, Liquid Retina display, 16GB unified memory."},
        {"XPS 13 Plus Laptop", "Dell", "Computers", "139990", "13.4-inch InfinityEdge, Intel Core Ultra 7, 16GB RAM, 512GB SSD."},
        {"ThinkPad X1 Carbon", "Lenovo", "Computers", "159990", "Business ultrabook, carbon-fibre chassis, Intel vPro, MIL-SPEC tested."},
        {"27-inch 4K Monitor", "LG", "Computers", "32999", "UHD IPS panel, USB-C 90W, HDR10, factory-calibrated colour."},
        {"Mechanical Keyboard TKL", "Keychron", "Computers", "8499", "Hot-swappable switches, RGB backlight, Bluetooth + USB-C, Mac/Win layout."},
        {"Ergonomic Wireless Mouse", "Logitech", "Computers", "6995", "MX Master series, 8K DPI, quiet clicks, multi-device flow, USB-C."},
        {"1TB NVMe SSD", "Samsung", "Computers", "8990", "PCIe 4.0, up to 7000MB/s read, 5-year warranty, great for game loading."},

        // --- Audio ---
        {"Noise-Cancelling Headphones QC", "Bose", "Audio", "26900", "World-class active noise cancellation, 24-hour battery, plush ear cushions."},
        {"WH-1000XM5 Headphones", "Sony", "Audio", "29990", "Industry-leading ANC, 30-hour battery, multipoint Bluetooth, LDAC."},
        {"Wireless Earbuds Pro", "Apple", "Audio", "24900", "Adaptive audio, USB-C, active noise cancellation, spatial audio."},
        {"Portable Bluetooth Speaker", "JBL", "Audio", "8999", "IP67 waterproof, 12-hour playtime, PartyBoost pairing, deep bass."},
        {"Studio Bookshelf Speakers", "Edifier", "Audio", "14999", "Active 2.0 monitors, Bluetooth 5.0, optical + RCA inputs, wood finish."},

        // --- Wearables ---
        {"Apple Watch Series 9", "Apple", "Wearables", "41900", "S9 chip, double-tap gesture, blood-oxygen, always-on Retina display."},
        {"Galaxy Watch 6 Classic", "Samsung", "Wearables", "36999", "Rotating bezel, body composition, sleep coaching, sapphire crystal."},
        {"Fitness Band 8", "Xiaomi", "Wearables", "3999", "1.62-inch AMOLED, 150+ sport modes, SpO2, 16-day battery life."},
        {"GPS Running Watch", "Garmin", "Wearables", "29990", "Multi-band GPS, training readiness, 14-day smartwatch battery."},

        // --- Home ---
        {"Robot Vacuum Cleaner", "iRobot", "Home", "32999", "LiDAR mapping, auto-empty dock, multi-floor plans, app + voice control."},
        {"Air Purifier HEPA", "Dyson", "Home", "44900", "Captures 99.95% of particles, real-time air-quality display, cool airflow."},
        {"Smart LED Bulb Pack", "Philips Hue", "Home", "5499", "16M colours, voice control, schedules, no hub required, set of 3."},
        {"Memory Foam Mattress Queen", "Sleepwell", "Home", "21999", "Orthopaedic support, breathable cover, medium-firm, 10-year warranty."},
        {"Cotton Bath Towel Set", "Trident", "Home", "1799", "600 GSM, quick-dry, ultra-soft, pack of 4, colour-fast dye."},

        // --- Kitchen ---
        {"Air Fryer 6.2L", "Philips", "Kitchen", "12995", "Rapid-air technology, 90% less fat, dishwasher-safe basket, digital presets."},
        {"Mixer Grinder 750W", "Bajaj", "Kitchen", "3499", "3 stainless jars, overload protection, multi-speed, 2-year warranty."},
        {"Espresso Machine", "DeLonghi", "Kitchen", "27990", "15-bar pump, milk frother, stainless steel, barista-style crema."},
        {"Cast Iron Skillet 12-inch", "Lodge", "Kitchen", "2999", "Pre-seasoned, oven-safe, even heat retention, lasts a lifetime."},
        {"Electric Kettle 1.7L", "Prestige", "Kitchen", "1299", "Stainless steel, auto shut-off, boil-dry protection, fast boil."},

        // --- Books ---
        {"Atomic Habits", "Penguin", "Books", "499", "James Clear's guide to building good habits and breaking bad ones."},
        {"Clean Code", "Pearson", "Books", "599", "Robert C. Martin on writing readable, maintainable software."},
        {"Designing Data-Intensive Applications", "O'Reilly", "Books", "1299", "Martin Kleppmann on the architecture of reliable, scalable systems."},
        {"Sapiens: A Brief History", "HarperCollins", "Books", "699", "Yuval Noah Harari on the rise of humankind."},
        {"The Pragmatic Programmer", "Addison-Wesley", "Books", "999", "Hunt & Thomas — timeless practices for the working developer."},

        // --- Clothing ---
        {"Men's Slim-Fit Jeans", "Levi's", "Clothing", "2999", "511 slim, stretch denim, mid-rise, classic 5-pocket styling."},
        {"Women's Cotton Kurta", "FabIndia", "Clothing", "1499", "Hand-block print, breathable cotton, regular fit, side slits."},
        {"Unisex Hooded Sweatshirt", "H&M", "Clothing", "1799", "Brushed fleece interior, kangaroo pocket, ribbed cuffs, relaxed fit."},
        {"Formal Cotton Shirt", "Van Heusen", "Clothing", "1999", "Wrinkle-resistant, slim fit, full sleeves, easy-iron finish."},
        {"Athletic Crew Socks 6-pack", "Nike", "Clothing", "1299", "Cushioned sole, arch support, moisture-wicking, breathable mesh."},

        // --- Footwear ---
        {"Ultraboost Running Shoes", "Adidas", "Footwear", "16999", "Responsive Boost midsole, Primeknit upper, Continental rubber outsole."},
        {"Air Zoom Pegasus", "Nike", "Footwear", "9995", "Daily trainer, Zoom Air units, breathable mesh, durable outsole."},
        {"Classic Leather Sneakers", "Puma", "Footwear", "4999", "Full-grain leather, cushioned footbed, everyday casual style."},
        {"Waterproof Hiking Boots", "Columbia", "Footwear", "8999", "Omni-Tech waterproof membrane, grippy lugs, ankle support."},

        // --- Sports ---
        {"Yoga Mat 6mm", "Decathlon", "Sports", "1299", "Non-slip TPE, extra cushioning, lightweight, carry strap included."},
        {"Adjustable Dumbbell Set", "Bowflex", "Sports", "34999", "5–24kg per hand, dial adjustment, replaces 15 sets of weights."},
        {"Cricket Bat English Willow", "SG", "Sports", "7999", "Grade 1 willow, full profile, traditional shape, ready to play."},
        {"Insulated Water Bottle 1L", "Milton", "Sports", "899", "Double-wall stainless steel, 24h cold / 12h hot, leak-proof."},

        // --- Beauty ---
        {"Vitamin C Face Serum", "Minimalist", "Beauty", "599", "10% vitamin C, brightening, fragrance-free, suits all skin types."},
        {"Sunscreen SPF 50 PA++++", "Neutrogena", "Beauty", "499", "Broad-spectrum, lightweight, non-greasy, water-resistant."},
        {"Hair Dryer 2200W", "Philips", "Beauty", "2199", "Ionic care, 3 heat settings, cool shot, foldable handle."},
        {"Matte Liquid Lipstick", "Maybelline", "Beauty", "650", "Transfer-proof, 16-hour wear, intense pigment, comfortable matte."},

        // --- Toys ---
        {"Building Bricks Classic 500pc", "LEGO", "Toys", "3499", "Creative open-ended building set, compatible bricks, ages 4+."},
        {"Remote Control Off-Road Car", "Hot Wheels", "Toys", "2499", "2.4GHz RC, all-terrain tyres, rechargeable, 1:16 scale."},
        {"Wooden Puzzle Set", "Melissa & Doug", "Toys", "999", "Educational shapes and numbers, chunky pieces, ages 2+."},

        // --- Gaming ---
        {"PlayStation 5 Slim", "Sony", "Gaming", "54990", "Ultra-high-speed SSD, ray tracing, DualSense haptics, 4K gaming."},
        {"Wireless Game Controller", "Microsoft", "Gaming", "5499", "Xbox controller, textured grips, hybrid D-pad, Bluetooth + USB-C."},
        {"Gaming Mechanical Headset", "HyperX", "Gaming", "7999", "7.1 surround, detachable mic, memory-foam earcups, multi-platform."},

        // --- Grocery ---
        {"Arabica Coffee Beans 1kg", "Blue Tokai", "Grocery", "899", "Single-origin medium roast, whole beans, freshly roasted to order."},
        {"Organic Honey 500g", "Dabur", "Grocery", "299", "Raw, unprocessed, NMR-tested purity, naturally sweet."},
        {"Extra Virgin Olive Oil 1L", "Figaro", "Grocery", "799", "Cold-pressed, rich aroma, ideal for dressings and cooking."},
    };
}
