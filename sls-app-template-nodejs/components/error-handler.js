module.exports = () => {
  const errorObj = {
    throwInputValidationError: (errorMessage) => ({
      statusCode: 400,
      body: errorMessage.toString(),
    }),
    throwForbiddenError: (errorMessage) => ({
      statusCode: 403,
      body: errorMessage.toString(),
    }),
    throwUnauthorizedError: (errorMessage) => ({
      statusCode: 401,
      body: errorMessage.toString(),
    }),
    throwNotFoundError: (errorMessage) => ({
      statusCode: 404,
      body: errorMessage.toString(),
    }),
    throwInternalServerError: (errorMessage) => ({
      statusCode: 500,
      body: errorMessage.toString(),
    }),
  };

  return errorObj;
};
