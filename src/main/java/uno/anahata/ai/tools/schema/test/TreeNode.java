/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.tools.schema.test;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "Represents a node in a tree structure.")
public class TreeNode {
    @Schema(description = "The data held by this node.", required = true)
    private String data;

    @Schema(description = "A list of child nodes.")
    private List<TreeNode> children;
}