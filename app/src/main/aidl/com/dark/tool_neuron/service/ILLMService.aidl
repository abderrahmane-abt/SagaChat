package com.dark.tool_neuron.service;

import com.dark.tool_neuron.service.IGgufGenerationCallback;
import com.dark.tool_neuron.service.IModelLoadCallback;

interface ILLMService {

    //Gguf
    void loadGgufModel(String modelPath, String modelName, String loadingParams, String inferenceParams, IModelLoadCallback callback);
    void generateGguf(String prompt, int maxTokens, IGgufGenerationCallback callback);
    void stopGenerationGguf();
    void unloadModelGguf();
    String getModelInfoGguf();

}