//
// MIT License
//
// Copyright (c) 2022 Alexander Söderberg & Contributors
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
package cloud.commandframework.sponge.parser;

import cloud.commandframework.arguments.parser.ArgumentParseResult;
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.arguments.parser.ParserDescriptor;
import cloud.commandframework.arguments.suggestion.Suggestion;
import cloud.commandframework.arguments.suggestion.SuggestionProvider;
import cloud.commandframework.brigadier.parser.WrappedBrigadierParser;
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.context.CommandInput;
import cloud.commandframework.sponge.NodeSource;
import cloud.commandframework.sponge.SpongeCommandContextKeys;
import cloud.commandframework.sponge.data.SingleEntitySelector;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.spongepowered.api.command.registrar.tree.CommandTreeNode;
import org.spongepowered.api.command.registrar.tree.CommandTreeNodeTypes;
import org.spongepowered.api.command.selector.Selector;
import org.spongepowered.api.entity.Entity;

/**
 * Argument for selecting a single {@link Entity} using a {@link Selector}.
 *
 * @param <C> sender type
 */
public final class SingleEntitySelectorParser<C> implements NodeSource, ArgumentParser.FutureArgumentParser<C, SingleEntitySelector>, SuggestionProvider<C> {

    public static <C> ParserDescriptor<C, SingleEntitySelector> singleEntitySelectorParser() {
        return ParserDescriptor.of(new SingleEntitySelectorParser<>(), SingleEntitySelector.class);
    }

    private final ArgumentParser<C, EntitySelector> nativeParser = new WrappedBrigadierParser<>(EntityArgument.entity());

    @Override
    public @NonNull CompletableFuture<ArgumentParseResult<@NonNull SingleEntitySelector>> parseFuture(
        @NonNull final CommandContext<@NonNull C> commandContext,
        @NonNull final CommandInput inputQueue
    ) {
        final CommandInput originalInput = inputQueue.copy();
        return this.nativeParser.parseFuture(commandContext, inputQueue).thenApply(result -> {
            if (result.failure().isPresent()) {
                return ArgumentParseResult.failure(result.failure().get());
            }
            final String consumedInput = originalInput.difference(inputQueue);
            final EntitySelector parsed = result.parsedValue().get();
            final Entity entity;
            try {
                entity = (Entity) parsed.findSingleEntity(
                    ((CommandSourceStack) commandContext.get(SpongeCommandContextKeys.COMMAND_CAUSE)).withPermission(2)
                );
            } catch (final CommandSyntaxException ex) {
                return ArgumentParseResult.failure(ex);
            }
            return ArgumentParseResult.success(new SingleEntitySelectorImpl((Selector) parsed, consumedInput, entity));
        });
    }

    @Override
    public @NonNull CompletableFuture<@NonNull Iterable<@NonNull Suggestion>> suggestionsFuture(
        final @NonNull CommandContext<C> context,
        final @NonNull CommandInput input
    ) {
        return this.nativeParser.suggestionProvider().suggestionsFuture(context, input);
    }

    @Override
    public CommandTreeNode.@NonNull Argument<? extends CommandTreeNode.Argument<?>> node() {
        return CommandTreeNodeTypes.ENTITY.get().createNode().single();
    }

    private static final class SingleEntitySelectorImpl implements SingleEntitySelector {

        private final Selector selector;
        private final String inputString;
        private final Entity result;

        private SingleEntitySelectorImpl(
            final Selector selector,
            final String inputString,
            final Entity result
        ) {
            this.selector = selector;
            this.inputString = inputString;
            this.result = result;
        }

        @Override
        public @NonNull Selector selector() {
            return this.selector;
        }

        @Override
        public @NonNull String inputString() {
            return this.inputString;
        }

        @Override
        public @NonNull Entity getSingle() {
            return this.result;
        }

    }

}
