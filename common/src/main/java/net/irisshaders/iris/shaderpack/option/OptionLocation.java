package net.irisshaders.iris.shaderpack.option;

import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;

public record OptionLocation(AbsolutePackPath filePath, int lineIndex) {
}
