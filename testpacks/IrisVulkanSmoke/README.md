# IrisVulkanSmoke

Minimal shader pack for verifying Iris's experimental Vulkan screen passes in game. It has no gbuffer or shadow
programs: `composite` writes a grayscale copy of the scene to `colortex1`, and `final` shows that on the left half
and the original scene on the right half, plus three 32x32 marker squares:

| Marker | Position     | Expected RGB (0-255)                               | Proves                                  |
|--------|--------------|----------------------------------------------------|-----------------------------------------|
| A      | bottom-left  | 64, 128, 191                                       | the final pass ran and reached the screen |
| B      | bottom-right | 128, 64, 255                                       | composite → colortex1 → final (flipping) |
| C      | top-right    | viewWidth/4096·255, viewHeight/4096·255, 255       | Iris uniforms arrive via the std140 block |

The split at the screen's horizontal centre (`texcoord.x < 0.5` in `final.fsh`) is deliberate: one frame shows both
the composite result and the untouched scene. For a test where every pixel of the screen carries a checkable value,
use `IrisVulkanFullscreen`.

With `-Diris.vulkan.pixelCheck=true`, Iris samples a 5x5 grid (corners, edges, centre) of the scene before the passes
and of the result after them, and compares each sample with a CPU model of this pack's shaders (grayscale of the
scene left of the split, the scene right of it, markers A/B/C at the corners):
`[Iris/Vulkan] Pixel check PASS|FAIL for IrisVulkanSmoke ... 25/25 samples match` in `logs/latest.log`.

Install: copy the `IrisVulkanSmoke` folder into the instance's `shaderpacks` folder.
