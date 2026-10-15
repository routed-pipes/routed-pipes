package routedpipes.adapter.fabric.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

import routedpipes.adapter.minecraft.PipeBlock;
import routedpipes.adapter.minecraft.PipeMounts;
import routedpipes.adapter.minecraft.RequestTableBlock;
import routedpipes.adapter.minecraft.content.Content;
import routedpipes.domain.look.Mount;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

// the block models are written for the obj and composite loaders of NeoForge, this reads the little of them they use
final class NeoForgeModels {

    private static final String OBJ = "neoforge:obj";
    private static final String COMPOSITE = "neoforge:composite";

    private NeoForgeModels() {
    }

    // model id to model, for every model of the mod that names one of the two loaders
    static CompletableFuture<Map<ResourceLocation, UnbakedModel>> load(ResourceManager resources, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            Map<ResourceLocation, Obj> meshes = new HashMap<>();
            Map<ResourceLocation, UnbakedModel> models = new HashMap<>();
            resources.listResources("models", path -> path.getNamespace().equals(Content.MODID) && path.getPath().endsWith(".json"))
                    .forEach((path, resource) -> {
                        JsonObject json;
                        try (Reader reader = resource.openAsReader()) {
                            json = JsonParser.parseReader(reader).getAsJsonObject();
                        } catch (IOException e) {
                            throw new IllegalStateException("could not read " + path, e);
                        }
                        UnbakedModel model = model(json, resources, meshes);
                        if (model != null) {
                            String id = path.getPath().substring("models/".length(), path.getPath().length() - ".json".length());
                            models.put(ResourceLocation.fromNamespaceAndPath(path.getNamespace(), id), model);
                        }
                    });
            return models;
        }, executor);
    }

    static void plugin(Map<ResourceLocation, UnbakedModel> models, ModelLoadingPlugin.Context context) {
        context.resolveModel().register(resolving -> models.get(resolving.id()));
        // no block state uses the mounts, so their models have to be asked for
        Mount.all().forEach(mount -> context.addModels(mountModel(mount)));
        Map<BakedModel, BakedModel> mounted = new IdentityHashMap<>();
        context.modifyModelAfterBake().register((model, baking) -> {
            if (model == null || baking.topLevelId() == null || baking.topLevelId().getVariant().equals("inventory")) {
                return model;
            }
            Block block = BuiltInRegistries.BLOCK.get(baking.topLevelId().id());
            if (!(block instanceof PipeBlock) || block instanceof RequestTableBlock) {
                return model;
            }
            Map<Mount, BakedModel> mounts = new HashMap<>();
            Mount.all().forEach(mount -> mounts.put(mount, baking.baker().bake(mountModel(mount), BlockModelRotation.X0_Y0)));
            // many states of a pipe share a model
            return mounted.computeIfAbsent(model, plain -> new MountedPipeModel(plain, mounts));
        });
    }

    private static ResourceLocation mountModel(Mount mount) {
        return ResourceLocation.fromNamespaceAndPath(Content.MODID, PipeMounts.modelPath(mount));
    }

    private static UnbakedModel model(JsonObject json, ResourceManager resources, Map<ResourceLocation, Obj> meshes) {
        String loader = json.has("loader") ? json.get("loader").getAsString() : "";
        if (loader.equals(OBJ)) {
            ResourceLocation mesh = ResourceLocation.parse(json.get("model").getAsString());
            Obj obj = meshes.computeIfAbsent(mesh, location -> Obj.read(resources, location));
            boolean flipV = json.has("flip_v") && json.get("flip_v").getAsBoolean();
            return new ObjModel(obj, flipV, textures(json));
        }
        if (loader.equals(COMPOSITE)) {
            List<UnbakedModel> children = new ArrayList<>();
            for (Map.Entry<String, JsonElement> child : json.getAsJsonObject("children").entrySet()) {
                UnbakedModel part = model(child.getValue().getAsJsonObject(), resources, meshes);
                if (part == null) {
                    throw new IllegalStateException("a part of a composite model is not an obj model: " + child.getKey());
                }
                children.add(part);
            }
            ResourceLocation parent = json.has("parent") ? ResourceLocation.parse(json.get("parent").getAsString()) : null;
            return new CompositeModel(children, parent, textures(json).get("particle"));
        }
        return null;
    }

    private static Map<String, String> textures(JsonObject json) {
        Map<String, String> textures = new HashMap<>();
        if (json.has("textures")) {
            json.getAsJsonObject("textures").entrySet().forEach(entry -> textures.put(entry.getKey(), entry.getValue().getAsString()));
        }
        return textures;
    }

    private static TextureAtlasSprite sprite(Function<Material, TextureAtlasSprite> sprites, String texture) {
        return sprites.apply(new Material(TextureAtlas.LOCATION_BLOCKS, ResourceLocation.parse(texture)));
    }

    // the faces of an obj file, each with the texture variable its material names
    private record Obj(List<Face> faces) {

        record Face(Vector3f[] positions, float[][] uvs, String texture) {
        }

        static Obj read(ResourceManager resources, ResourceLocation location) {
            List<Vector3f> positions = new ArrayList<>();
            List<float[]> uvs = new ArrayList<>();
            List<Face> faces = new ArrayList<>();
            Map<String, String> materials = new HashMap<>();
            String texture = null;
            for (String line : lines(resources, location)) {
                String[] parts = line.trim().split("\\s+");
                switch (parts[0]) {
                    case "mtllib" -> materials.putAll(materials(resources, location.withPath(path -> path.substring(0, path.lastIndexOf('/') + 1) + parts[1])));
                    case "usemtl" -> texture = materials.get(parts[1]);
                    case "v" -> positions.add(new Vector3f(Float.parseFloat(parts[1]), Float.parseFloat(parts[2]), Float.parseFloat(parts[3])));
                    case "vt" -> uvs.add(new float[] {Float.parseFloat(parts[1]), Float.parseFloat(parts[2])});
                    case "f" -> {
                        int corners = parts.length - 1;
                        Vector3f[] facePositions = new Vector3f[corners];
                        float[][] faceUvs = new float[corners][];
                        for (int corner = 0; corner < corners; corner++) {
                            String[] indices = parts[corner + 1].split("/");
                            facePositions[corner] = positions.get(Integer.parseInt(indices[0]) - 1);
                            faceUvs[corner] = indices.length > 1 && !indices[1].isEmpty() ? uvs.get(Integer.parseInt(indices[1]) - 1) : new float[] {0, 0};
                        }
                        faces.add(new Face(facePositions, faceUvs, texture));
                    }
                    default -> {
                    }
                }
            }
            return new Obj(faces);
        }

        // material name to the texture variable of its diffuse map, like frame for #frame
        private static Map<String, String> materials(ResourceManager resources, ResourceLocation location) {
            Map<String, String> materials = new HashMap<>();
            String material = null;
            for (String line : lines(resources, location)) {
                String[] parts = line.trim().split("\\s+");
                if (parts[0].equals("newmtl")) {
                    material = parts[1];
                } else if (parts[0].equals("map_Kd") && material != null) {
                    materials.put(material, parts[1].startsWith("#") ? parts[1].substring(1) : parts[1]);
                }
            }
            return materials;
        }

        private static List<String> lines(ResourceManager resources, ResourceLocation location) {
            try (BufferedReader reader = resources.getResourceOrThrow(location).openAsReader()) {
                return reader.lines().toList();
            } catch (IOException e) {
                throw new IllegalStateException("could not read " + location, e);
            }
        }
    }

    private record ObjModel(Obj obj, boolean flipV, Map<String, String> textures) implements UnbakedModel {

        @Override
        public Collection<ResourceLocation> getDependencies() {
            return List.of();
        }

        @Override
        public void resolveParents(Function<ResourceLocation, UnbakedModel> models) {
        }

        @Override
        public BakedModel bake(ModelBaker baker, Function<Material, TextureAtlasSprite> sprites, ModelState state) {
            String particle = textures.getOrDefault("particle", textures.values().stream().findFirst().orElse("minecraft:missingno"));
            return new SimpleBakedModel(quads(sprites, state), Map.of(Direction.UP, List.of(), Direction.DOWN, List.of(),
                    Direction.NORTH, List.of(), Direction.SOUTH, List.of(), Direction.EAST, List.of(), Direction.WEST, List.of()),
                    true, true, true, sprite(sprites, particle), ItemTransforms.NO_TRANSFORMS, ItemOverrides.EMPTY);
        }

        List<BakedQuad> quads(Function<Material, TextureAtlasSprite> sprites, ModelState state) {
            Matrix4f rotation = state.getRotation().getMatrix();
            List<BakedQuad> quads = new ArrayList<>();
            for (Obj.Face face : obj.faces()) {
                String texture = textures.get(face.texture());
                if (texture == null) {
                    continue;
                }
                TextureAtlasSprite sprite = sprite(sprites, texture);
                // a triangle is a quad with its last corner twice
                int[] vertices = new int[32];
                Vector3f[] corners = new Vector3f[4];
                for (int corner = 0; corner < 4; corner++) {
                    int from = Math.min(corner, face.positions().length - 1);
                    Vector4f moved = rotation.transform(new Vector4f(face.positions()[from].x() - 0.5f,
                            face.positions()[from].y() - 0.5f, face.positions()[from].z() - 0.5f, 1));
                    corners[corner] = new Vector3f(moved.x() + 0.5f, moved.y() + 0.5f, moved.z() + 0.5f);
                    float u = face.uvs()[from][0];
                    float v = flipV ? 1 - face.uvs()[from][1] : face.uvs()[from][1];
                    int offset = corner * 8;
                    vertices[offset] = Float.floatToRawIntBits(corners[corner].x());
                    vertices[offset + 1] = Float.floatToRawIntBits(corners[corner].y());
                    vertices[offset + 2] = Float.floatToRawIntBits(corners[corner].z());
                    vertices[offset + 3] = -1;
                    vertices[offset + 4] = Float.floatToRawIntBits(sprite.getU(u));
                    vertices[offset + 5] = Float.floatToRawIntBits(sprite.getV(v));
                }
                Vector3f normal = new Vector3f(corners[1]).sub(corners[0]).cross(new Vector3f(corners[2]).sub(corners[0]));
                quads.add(new BakedQuad(vertices, -1, Direction.getNearest(normal.x(), normal.y(), normal.z()), sprite, true));
            }
            return quads;
        }
    }

    // the parts drawn together, with the item transforms of the parent
    private static final class CompositeModel implements UnbakedModel {

        private final List<UnbakedModel> children;
        private final ResourceLocation parentId;
        private final String particle;
        private UnbakedModel parent;

        CompositeModel(List<UnbakedModel> children, ResourceLocation parentId, String particle) {
            this.children = children;
            this.parentId = parentId;
            this.particle = particle;
        }

        @Override
        public Collection<ResourceLocation> getDependencies() {
            return parentId == null ? List.of() : List.of(parentId);
        }

        @Override
        public void resolveParents(Function<ResourceLocation, UnbakedModel> models) {
            if (parentId != null) {
                parent = models.apply(parentId);
                parent.resolveParents(models);
            }
        }

        @Override
        public BakedModel bake(ModelBaker baker, Function<Material, TextureAtlasSprite> sprites, ModelState state) {
            List<BakedQuad> quads = new ArrayList<>();
            for (UnbakedModel child : children) {
                quads.addAll(((ObjModel) child).quads(sprites, state));
            }
            ItemTransforms transforms = parent instanceof BlockModel block ? block.getTransforms() : ItemTransforms.NO_TRANSFORMS;
            Map<Direction, List<BakedQuad>> culled = new LinkedHashMap<>();
            for (Direction direction : Direction.values()) {
                culled.put(direction, List.of());
            }
            return new SimpleBakedModel(quads, culled, true, true, true,
                    sprite(sprites, particle == null ? "minecraft:missingno" : particle), transforms, ItemOverrides.EMPTY);
        }
    }

    // a pipe with the mounts that hold it to the solid blocks around it
    // which ones depends on those blocks, so they are picked whenever the chunk is drawn again
    private static final class MountedPipeModel extends ForwardingBakedModel {

        private final Map<Mount, BakedModel> mounts;

        MountedPipeModel(BakedModel pipe, Map<Mount, BakedModel> mounts) {
            this.wrapped = pipe;
            this.mounts = mounts;
        }

        @Override
        public boolean isVanillaAdapter() {
            return false;
        }

        @Override
        public void emitBlockQuads(BlockAndTintGetter level, BlockState state, BlockPos pos, Supplier<RandomSource> random,
                RenderContext context) {
            super.emitBlockQuads(level, state, pos, random, context);
            for (Mount mount : PipeMounts.at(level, pos, state)) {
                mounts.get(mount).emitBlockQuads(level, state, pos, random, context);
            }
        }
    }
}
