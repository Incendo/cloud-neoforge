//
// MIT License
//
// Copyright (c) 2024 Incendo
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//
package org.incendo.cloud.sponge.parser;

import com.google.common.base.Suppliers;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.commands.arguments.DimensionArgument;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.sponge.NodeSource;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;
import org.spongepowered.api.ResourceKey;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.registrar.tree.CommandTreeNode;
import org.spongepowered.api.command.registrar.tree.CommandTreeNodeTypes;
import org.spongepowered.api.world.server.ServerWorld;
import org.spongepowered.api.world.server.WorldManager;

/**
 * Argument for retrieving {@link ServerWorld ServerWorlds} from the {@link WorldManager} by their {@link ResourceKey}.
 *
 * @param <C> command sender type
 */
public final class WorldParser<C> implements ArgumentParser<C, ServerWorld>, NodeSource, BlockingSuggestionProvider.Strings<C> {

    /**
     * Creates a new {@link WorldParser}.
     *
     * @param <C> command sender type
     * @return new parser
     */
    public static <C> ParserDescriptor<C, ServerWorld> worldParser() {
        return ParserDescriptor.of(new WorldParser<>(), ServerWorld.class);
    }

    private static final Supplier<DynamicCommandExceptionType> ERROR_INVALID_VALUE = Suppliers.memoize(() -> {
        try {
            // ERROR_INVALID_VALUE (todo: use accessor)
            final Field errorInvalidValueField = Arrays.stream(DimensionArgument.class.getDeclaredFields())
                .filter(f -> f.getType().equals(DynamicCommandExceptionType.class))
                .findFirst()
                .orElseThrow(IllegalStateException::new);
            errorInvalidValueField.setAccessible(true);
            return (DynamicCommandExceptionType) errorInvalidValueField.get(null);
        } catch (final Exception ex) {
            throw new RuntimeException("Couldn't access ERROR_INVALID_VALUE command exception type.", ex);
        }
    });

    @Override
    public @NonNull ArgumentParseResult<@NonNull ServerWorld> parse(
        final @NonNull CommandContext<@NonNull C> commandContext,
        final @NonNull CommandInput inputQueue
    ) {
        final String input = inputQueue.readString();
        final ResourceKey key = ResourceKeyUtil.resourceKey(input);
        if (key == null) {
            return ResourceKeyUtil.invalidResourceKey();
        }
        final Optional<ServerWorld> entry = Sponge.server().worldManager().world(key);
        if (entry.isPresent()) {
            return ArgumentParseResult.success(entry.get());
        }
        return ArgumentParseResult.failure(ERROR_INVALID_VALUE.get().create(key));
    }

    @Override
    public @NonNull List<@NonNull String> stringSuggestions(
        final @NonNull CommandContext<C> commandContext,
        final @NonNull CommandInput input
    ) {
        return Sponge.server().worldManager().worlds().stream().flatMap(world -> {
            if (!input.isEmpty() && world.key().namespace().equals(ResourceKey.MINECRAFT_NAMESPACE)) {
                return Stream.of(world.key().value(), world.key().asString());
            }
            return Stream.of(world.key().asString());
        }).collect(Collectors.toList());
    }

    @Override
    public CommandTreeNode.@NonNull Argument<? extends CommandTreeNode.Argument<?>> node() {
        return CommandTreeNodeTypes.RESOURCE_LOCATION.get().createNode().customCompletions();
    }

}
