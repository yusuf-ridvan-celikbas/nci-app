// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "CansinCore",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [.library(name: "CansinCore", targets: ["CansinCore"])],
    targets: [
        .target(name: "CansinCore"),
        .testTarget(name: "CansinCoreTests", dependencies: ["CansinCore"]),
    ]
)
