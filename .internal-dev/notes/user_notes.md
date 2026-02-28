We need to focus on our error paths now that we have an onError hook we need to make sure that all session branches even
if they result in an error do not propagate it but instead catch it emit the error callback if it exists (it should always but up to implementor)
and not disrupt any other hooks callbacks or logic 