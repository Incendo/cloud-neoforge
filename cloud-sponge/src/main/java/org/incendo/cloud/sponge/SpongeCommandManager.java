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
package org.incendo.cloud.sponge;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Module;
import io.leangen.geantyref.TypeToken;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.SenderMapperHolder;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.meta.CommandMeta;
import org.incendo.cloud.meta.SimpleCommandMeta;
import org.incendo.cloud.parser.ParserParameters;
import org.incendo.cloud.sponge.annotation.specifier.Center;
import org.incendo.cloud.sponge.parser.BlockInputParser;
import org.incendo.cloud.sponge.parser.BlockPredicateParser;
import org.incendo.cloud.sponge.parser.ComponentParser;
import org.incendo.cloud.sponge.parser.DataContainerParser;
import org.incendo.cloud.sponge.parser.GameProfileCollectionParser;
import org.incendo.cloud.sponge.parser.GameProfileParser;
import org.incendo.cloud.sponge.parser.ItemStackPredicateParser;
import org.incendo.cloud.sponge.parser.MultipleEntitySelectorParser;
import org.incendo.cloud.sponge.parser.MultiplePlayerSelectorParser;
import org.incendo.cloud.sponge.parser.NamedTextColorParser;
import org.incendo.cloud.sponge.parser.OperatorParser;
import org.incendo.cloud.sponge.parser.ProtoItemStackParser;
import org.incendo.cloud.sponge.parser.RegistryEntryParser;
import org.incendo.cloud.sponge.parser.ResourceKeyParser;
import org.incendo.cloud.sponge.parser.SingleEntitySelectorParser;
import org.incendo.cloud.sponge.parser.SinglePlayerSelectorParser;
import org.incendo.cloud.sponge.parser.UserParser;
import org.incendo.cloud.sponge.parser.Vector2dParser;
import org.incendo.cloud.sponge.parser.Vector2iParser;
import org.incendo.cloud.sponge.parser.Vector3dParser;
import org.incendo.cloud.sponge.parser.Vector3iParser;
import org.incendo.cloud.sponge.parser.WorldParser;
import org.incendo.cloud.sponge.suggestion.SpongeSuggestion;
import org.incendo.cloud.state.RegistrationState;
import org.incendo.cloud.suggestion.SuggestionFactory;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandCause;
import org.spongepowered.api.event.lifecycle.RegisterCommandEvent;
import org.spongepowered.api.registry.DefaultedRegistryType;
import org.spongepowered.api.registry.Registry;
import org.spongepowered.api.registry.RegistryType;
import org.spongepowered.api.registry.RegistryTypes;
import org.spongepowered.math.vector.Vector2d;
import org.spongepowered.math.vector.Vector3d;
import org.spongepowered.plugin.PluginContainer;

/**
 * Command manager for Sponge API v8.
 * <p>
 * The manager supports Guice injection
 * as long as the {@link CloudInjectionModule} is present in the injector.
 * This can be achieved by using {@link com.google.inject.Injector#createChildInjector(Module...)}
 *
 * @param <C> Command sender type
 */
public final class SpongeCommandManager<C> extends CommandManager<C> implements SenderMapperHolder<CommandCause, C> {

    private final PluginContainer pluginContainer;
    private final SenderMapper<CommandCause, C> senderMapper;
    private final SpongeParserMapper<C> parserMapper;
    private final SuggestionFactory<C, SpongeSuggestion> suggestionFactory;

    /**
     * Create a new command manager instance
     *
     * @param pluginContainer      Owning plugin
     * @param executionCoordinator Execution coordinator instance
     * @param senderMapper         Function mapping the custom command sender type to a Sponge CommandCause
     */
    @SuppressWarnings("unchecked")
    @Inject
    public SpongeCommandManager(
        final @NonNull PluginContainer pluginContainer,
        final @NonNull ExecutionCoordinator<C> executionCoordinator,
        final @NonNull SenderMapper<CommandCause, C> senderMapper
    ) {
        super(executionCoordinator, new SpongeRegistrationHandler<C>());
        this.checkLateCreation();
        this.pluginContainer = pluginContainer;
        ((SpongeRegistrationHandler<C>) this.commandRegistrationHandler()).initialize(this);
        this.senderMapper = senderMapper;
        this.parserMapper = new SpongeParserMapper<>();
        this.registerCommandPreProcessor(new SpongeCommandPreprocessor<>(this));
        this.registerParsers();
        this.captionRegistry().registerProvider(new SpongeDefaultCaptionsProvider<>());
        this.suggestionFactory = super.suggestionFactory().mapped(SpongeSuggestion::spongeSuggestion);

        SpongeDefaultExceptionHandlers.register(this);
    }

    @Override
    public @NonNull SuggestionFactory<C, SpongeSuggestion> suggestionFactory() {
        return this.suggestionFactory;
    }

    private void checkLateCreation() {
        // Not the most accurate check, but will at least catch creation attempted after the server has started
        if (!Sponge.isServerAvailable()) {
            return;
        }
        throw new IllegalStateException(
            "SpongeCommandManager must be created before the first firing of RegisterCommandEvent. (created too late)"
        );
    }

    private void registerParsers() {
        this.parserRegistry()
            .registerParser(ComponentParser.componentParser())
            .registerParser(NamedTextColorParser.namedTextColorParser())
            .registerParser(OperatorParser.operatorParser())
            .registerParser(WorldParser.worldParser())
            .registerParser(ProtoItemStackParser.protoItemStackParser())
            .registerParser(ItemStackPredicateParser.itemStackPredicateParser())
            .registerParser(ResourceKeyParser.resourceKeyParser())
            .registerParser(GameProfileParser.gameProfileParser())
            .registerParser(GameProfileCollectionParser.gameProfileCollectionParser())
            .registerParser(BlockInputParser.blockInputParser())
            .registerParser(BlockPredicateParser.blockPredicateParser())
            .registerParser(UserParser.userParser())
            .registerParser(DataContainerParser.dataContainerParser())
            .registerAnnotationMapper(
                Center.class,
                (annotation, type) -> ParserParameters.single(SpongeParserParameters.CENTER_INTEGERS, true)
            )
            .registerParserSupplier(
                TypeToken.get(Vector2d.class),
                params -> new Vector2dParser<>(params.get(SpongeParserParameters.CENTER_INTEGERS, false))
            )
            .registerParserSupplier(
                TypeToken.get(Vector3d.class),
                params -> new Vector3dParser<>(params.get(SpongeParserParameters.CENTER_INTEGERS, false))
            )
            .registerParser(Vector2iParser.vector2iParser())
            .registerParser(Vector3iParser.vector3iParser())
            .registerParser(SinglePlayerSelectorParser.singlePlayerSelectorParser())
            .registerParser(MultiplePlayerSelectorParser.multiplePlayerSelectorParser())
            .registerParser(SingleEntitySelectorParser.singleEntitySelectorParser())
            .registerParser(MultipleEntitySelectorParser.multipleEntitySelectorParser());

        this.registerRegistryParsers();
    }

    private void registerRegistryParsers() {
        final Set<RegistryType<?>> ignoredRegistryTypes = ImmutableSet.of(
            RegistryTypes.OPERATOR // We have a different Operator parser that doesn't use a ResourceKey as input
        );
        for (final Field field : RegistryTypes.class.getDeclaredFields()) {
            final Type generic = field.getGenericType(); /* RegistryType<?> */
            if (!(generic instanceof ParameterizedType)) {
                continue;
            }

            final RegistryType<?> registryType;
            try {
                registryType = (RegistryType<?>) field.get(null);
            } catch (final IllegalAccessException ex) {
                throw new RuntimeException("Failed to access RegistryTypes." + field.getName(), ex);
            }
            if (ignoredRegistryTypes.contains(registryType) || !(registryType instanceof DefaultedRegistryType)) {
                continue;
            }
            final DefaultedRegistryType<?> defaultedRegistryType = (DefaultedRegistryType<?>) registryType;
            final Type valueType = ((ParameterizedType) generic).getActualTypeArguments()[0];

            this.parserRegistry().registerParserSupplier(
                TypeToken.get(valueType),
                params -> new RegistryEntryParser<>(defaultedRegistryType)
            );
        }
    }

    @Override
    public boolean hasPermission(
        final @NonNull C sender,
        final @NonNull String permission
    ) {
        if (permission.isEmpty()) {
            return true;
        }
        return this.senderMapper.reverse(sender).hasPermission(permission);
    }

    @Override
    public @NonNull CommandMeta createDefaultCommandMeta() {
        return SimpleCommandMeta.empty();
    }

    /**
     * Get the {@link PluginContainer} of the plugin that owns this command manager.
     *
     * @return plugin container
     */
    public @NonNull PluginContainer owningPluginContainer() {
        return this.pluginContainer;
    }

    /**
     * Get the {@link SpongeParserMapper}, responsible for mapping Cloud
     * {@link org.incendo.cloud.parser.ArgumentParser ArgumentParser} to Sponge
     * {@link org.spongepowered.api.command.registrar.tree.CommandTreeNode.Argument CommandTreeNode.Arguments}.
     *
     * @return the parser mapper
     */
    public @NonNull SpongeParserMapper<C> parserMapper() {
        return this.parserMapper;
    }

    @Override
    public @NonNull SenderMapper<CommandCause, C> senderMapper() {
        return this.senderMapper;
    }

    void registrationCalled() {
        if (!this.registrationCallbackListeners.isEmpty()) {
            this.registrationCallbackListeners.forEach(listener -> listener.accept(this));
            this.registrationCallbackListeners.clear();
        }
        if (this.state() != RegistrationState.AFTER_REGISTRATION) {
            this.lockRegistration();
        }
    }

    private final Set<Consumer<SpongeCommandManager<C>>> registrationCallbackListeners = new HashSet<>();

    /**
     * Add a listener to the command registration callback.
     *
     * <p>These listeners will be called just before command registration is finalized
     * (during the first invocation of Cloud's internal {@link RegisterCommandEvent} listener).</p>
     *
     * <p>This allows for registering commands at the latest possible point in the plugin
     * lifecycle, which may be necessary for certain {@link Registry Registries} to have
     * initialized.</p>
     *
     * @param listener listener
     */
    public void addRegistrationCallbackListener(final @NonNull Consumer<@NonNull SpongeCommandManager<C>> listener) {
        if (this.state() == RegistrationState.AFTER_REGISTRATION) {
            throw new IllegalStateException("The SpongeCommandManager is in the AFTER_REGISTRATION state!");
        }
        this.registrationCallbackListeners.add(listener);
    }

}
