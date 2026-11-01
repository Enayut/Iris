# IrisVulkanFullscreen

Full-screen validation pack for Iris's experimental Vulkan screen passes. Unlike `IrisVulkanSmoke`, whose split
(grayscale left half, colour right half) is a deliberate diagnostic, this pack makes every pixel of the screen
predictable:

- `composite` writes its texture coordinate `(u, v)` into `colortex1`.
- `final` outputs red = `u`, green = `v` (read back from `colortex1`) and blue = luminance of the scene (`colortex0`).

On screen this is a red-to-right, green-to-top gradient over a dim blue copy of the world. Black at the bottom-left
corner, yellow at the top-right.

With `-Diris.vulkan.pixelCheck=true`, Iris samples a 5x5 grid (corners, edges, centre) of the scene before the passes
and of the result after them, and compares each sample with the CPU model in `IrisVulkanPixelModel`:
`[Iris/Vulkan] Pixel check PASS|FAIL for IrisVulkanFullscreen ... 25/25 samples match`.

Install: copy the `IrisVulkanFullscreen` folder into the instance's `shaderpacks` folder and set
`shaderPack=IrisVulkanFullscreen` in `config/iris.properties`.
